package cn.hengxing.inkpress.modules.evolution.service;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.ai.service.AiChatService;
import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.evolution.entity.AiFeedback;
import cn.hengxing.inkpress.modules.evolution.entity.EvolutionRun;
import cn.hengxing.inkpress.modules.evolution.entity.MemorySnapshot;
import cn.hengxing.inkpress.modules.evolution.mapper.AiFeedbackMapper;
import cn.hengxing.inkpress.modules.evolution.mapper.EvolutionRunMapper;
import cn.hengxing.inkpress.modules.evolution.mapper.MemorySnapshotMapper;
import cn.hengxing.inkpress.modules.evolution.mapper.WritingMemoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 进化闭环编排（BoHack 日新赛道）：运行会话 → 作者反馈 → 指标核算 → 记忆蒸馏 → 快照/回退。
 * 演示口径：同一任务跑两次，第二次因注入第一次蒸馏的记忆而指标更优；
 * "学到什么"看新增记忆行，"回退"看快照恢复后行为还原。
 */
@Service
public class EvolutionService {

    private static final Logger log = LoggerFactory.getLogger(EvolutionService.class);

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_FINISHED = "FINISHED";
    private static final int SUGGESTION_CAP = 8000;
    private static final int DISTANCE_CAP_CHARS = 8000;

    private final EvolutionRunMapper runMapper;
    private final AiFeedbackMapper feedbackMapper;
    private final MemorySnapshotMapper snapshotMapper;
    private final WritingMemoryMapper memoryMapper;
    private final WritingMemoryService memoryService;
    private final AiChatService aiChatService;
    private final ArticleMapper articleMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EvolutionService(EvolutionRunMapper runMapper, AiFeedbackMapper feedbackMapper,
                            MemorySnapshotMapper snapshotMapper, WritingMemoryMapper memoryMapper,
                            WritingMemoryService memoryService, AiChatService aiChatService,
                            ArticleMapper articleMapper) {
        this.runMapper = runMapper;
        this.feedbackMapper = feedbackMapper;
        this.snapshotMapper = snapshotMapper;
        this.memoryMapper = memoryMapper;
        this.memoryService = memoryService;
        this.aiChatService = aiChatService;
        this.articleMapper = articleMapper;
    }

    // ===== 运行会话 =====

    /** 开始一次运行；若已有 RUNNING（上次忘关）自动按现有数据收尾后再开 */
    public EvolutionRun startRun(Long userId, Long articleId) {
        EvolutionRun running = currentRun(userId);
        if (running != null) {
            finishRun(userId, running.getId(), null, List.of());
        }
        EvolutionRun run = new EvolutionRun();
        run.setUserId(userId);
        run.setTaskKind("WRITING_ASSIST");
        run.setStatus(STATUS_RUNNING);
        run.setArticleId(articleId);
        run.setStartedAt(LocalDateTime.now());
        runMapper.insert(run);
        return run;
    }

    public EvolutionRun currentRun(Long userId) {
        return runMapper.selectOne(new LambdaQueryWrapper<EvolutionRun>()
                .eq(EvolutionRun::getUserId, userId)
                .eq(EvolutionRun::getStatus, STATUS_RUNNING)
                .orderByDesc(EvolutionRun::getId)
                .last("LIMIT 1"));
    }

    /** 记录作者反馈（👍/👎/采纳）；未指定 run 时自动挂到进行中的运行 */
    public AiFeedback recordFeedback(Long userId, Long runId, Long articleId,
                                     String type, String text, String verdict) {
        String t = type == null ? "CHAT" : type.toUpperCase();
        if (!List.of("REPLACE", "APPEND", "CHAT").contains(t)) {
            throw BizException.of(3201, "未知反馈类型：" + type);
        }
        String v = verdict == null ? "" : verdict.toUpperCase();
        if (!List.of("UP", "DOWN", "APPLIED").contains(v)) {
            throw BizException.of(3202, "未知反馈结论：" + verdict);
        }
        if (runId == null) {
            EvolutionRun running = currentRun(userId);
            runId = running == null ? null : running.getId();
        } else {
            EvolutionRun run = runMapper.selectById(runId);
            if (run == null || !run.getUserId().equals(userId)) {
                throw BizException.of(3203, "运行不存在");
            }
        }
        AiFeedback fb = new AiFeedback();
        fb.setRunId(runId);
        fb.setUserId(userId);
        fb.setArticleId(articleId);
        fb.setSuggestionType(t);
        fb.setSuggestionText(cap(text, SUGGESTION_CAP));
        fb.setVerdict(v);
        feedbackMapper.insert(fb);
        return fb;
    }

    /** 结束运行：核算指标 → 持久化 → 蒸馏记忆（AI 失败时降级为原文纠正入库，学习不断链） */
    public EvolutionRun finishRun(Long userId, Long runId, String finalContent, List<String> corrections) {
        EvolutionRun run = runMapper.selectById(runId);
        if (run == null || !run.getUserId().equals(userId)) {
            throw BizException.of(3203, "运行不存在");
        }
        if (!STATUS_RUNNING.equals(run.getStatus())) {
            throw BizException.of(3204, "运行已结束");
        }
        if (finalContent == null || finalContent.isBlank()) {
            finalContent = loadArticleContent(run.getArticleId());
        }
        List<AiFeedback> feedbacks = feedbackMapper.selectList(new LambdaQueryWrapper<AiFeedback>()
                .eq(AiFeedback::getRunId, runId));

        int applied = 0;
        int verbatim = 0;
        int thumbsUp = 0;
        int thumbsDown = 0;
        int distanceSum = 0;
        int distanceCount = 0;
        String finalNorm = normalize(finalContent);
        for (AiFeedback fb : feedbacks) {
            switch (fb.getVerdict() == null ? "" : fb.getVerdict()) {
                case "APPLIED" -> {
                    applied++;
                    String sug = normalize(fb.getSuggestionText());
                    if (!sug.isEmpty() && !finalNorm.isEmpty()) {
                        if (finalNorm.contains(sug)) {
                            fb.setFinalVerbatim(1);
                            fb.setEditDistance(0);
                            verbatim++;
                            distanceSum += 0;
                            distanceCount++;
                        } else {
                            int bp = levenshteinBp(sug, finalNorm);
                            fb.setFinalVerbatim(0);
                            fb.setEditDistance(bp);
                            distanceSum += bp;
                            distanceCount++;
                        }
                        feedbackMapper.updateById(fb);
                    }
                }
                case "UP" -> thumbsUp++;
                case "DOWN" -> thumbsDown++;
                default -> { /* ignore */ }
            }
        }
        List<String> safeCorrections = corrections == null ? List.of() : corrections;
        Map<String, Object> metrics = buildMetrics(feedbacks.size(), applied, verbatim, thumbsUp,
                thumbsDown, distanceCount == 0 ? 0 : distanceSum / distanceCount,
                safeCorrections.size(), 0, false);

        // 蒸馏：AI 把反馈提炼为持久偏好；失败/未配置时把纠正原话直接入库，保证学习可见
        DistillResult dr = distill(userId, runId, feedbacks, safeCorrections);
        metrics.put("memoryLearned", dr.count());
        metrics.put("distilled", dr.byAi());

        run.setStatus(STATUS_FINISHED);
        run.setFinishedAt(LocalDateTime.now());
        run.setMetricsJson(toJson(metrics));
        runMapper.updateById(run);
        return run;
    }

    // ===== 快照 / 回退 =====

    public MemorySnapshot snapshot(Long userId, String label) {
        MemorySnapshot s = new MemorySnapshot();
        s.setUserId(userId);
        s.setLabel(label == null || label.isBlank() ? "运行前快照" : cap(label, 100));
        s.setSnapshotJson(memoryService.snapshotJson(userId));
        snapshotMapper.insert(s);
        return s;
    }

    /** 回退：现存 ACTIVE 全部失效，快照条目复制回 ACTIVE（幂等，可反复演示） */
    public Map<String, Object> rollback(Long userId, Long snapshotId) {
        MemorySnapshot s = snapshotMapper.selectById(snapshotId);
        if (s == null || !s.getUserId().equals(userId)) {
            throw BizException.of(3205, "快照不存在");
        }
        int superseded = memoryService.supersedeAllActive(userId);
        int restored = 0;
        for (Map<String, Object> e : memoryService.parseSnapshot(s.getSnapshotJson())) {
            memoryService.insert(userId, String.valueOf(e.getOrDefault("category", "STYLE")),
                    String.valueOf(e.getOrDefault("content", "")), "RESTORED", asLong(e.get("originRunId")));
            restored++;
        }
        return Map.of("superseded", superseded, "restored", restored);
    }

    // ===== 查询 =====

    /** 公开摘要（AI 线 / /ai/evolution 数据源）：仅聚合指标，不暴露用户隐私字段 */
    public Map<String, Object> publicSummary() {
        Long finishedRuns = runMapper.selectCount(new LambdaQueryWrapper<EvolutionRun>()
                .eq(EvolutionRun::getStatus, STATUS_FINISHED));
        Long activeMemories = memoryMapper.selectCount(new LambdaQueryWrapper<cn.hengxing.inkpress.modules.evolution.entity.WritingMemory>()
                .eq(cn.hengxing.inkpress.modules.evolution.entity.WritingMemory::getStatus, "ACTIVE"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("finishedRuns", finishedRuns);
        m.put("activeMemories", activeMemories);
        EvolutionRun last = runMapper.selectOne(new LambdaQueryWrapper<EvolutionRun>()
                .eq(EvolutionRun::getStatus, STATUS_FINISHED)
                .orderByDesc(EvolutionRun::getId)
                .last("LIMIT 1"));
        if (last != null) {
            m.put("lastRunId", last.getId());
            if (last.getMetricsJson() != null) {
                try {
                    m.put("lastMetrics", objectMapper.readTree(last.getMetricsJson()));
                } catch (Exception ignore) {
                    m.put("lastMetricsRaw", last.getMetricsJson());
                }
            }
            if (last.getFinishedAt() != null) {
                m.put("lastFinishedAt", last.getFinishedAt().toString());
            }
        }
        return m;
    }

    public List<EvolutionRun> listRuns(Long userId) {
        return runMapper.selectList(new LambdaQueryWrapper<EvolutionRun>()
                .eq(EvolutionRun::getUserId, userId)
                .orderByDesc(EvolutionRun::getId)
                .last("LIMIT 20"));
    }

    public List<WritingMemoryLike> listMemory(Long userId) {
        List<cn.hengxing.inkpress.modules.evolution.entity.WritingMemory> rows =
                memoryMapper.selectList(new LambdaQueryWrapper<cn.hengxing.inkpress.modules.evolution.entity.WritingMemory>()
                        .eq(cn.hengxing.inkpress.modules.evolution.entity.WritingMemory::getUserId, userId)
                        .orderByDesc(cn.hengxing.inkpress.modules.evolution.entity.WritingMemory::getCreatedAt)
                        .last("LIMIT 50"));
        List<WritingMemoryLike> out = new ArrayList<>();
        for (var m : rows) {
            out.add(new WritingMemoryLike(m.getId(), m.getCategory(), m.getContent(), m.getSource(),
                    m.getStatus(), m.getOriginRunId(), m.getLastHitAt(), m.getCreatedAt()));
        }
        return out;
    }

    public List<MemorySnapshot> listSnapshots(Long userId) {
        return snapshotMapper.selectList(new LambdaQueryWrapper<MemorySnapshot>()
                .eq(MemorySnapshot::getUserId, userId)
                .orderByDesc(MemorySnapshot::getId)
                .last("LIMIT 10"));
    }

    // ===== 蒸馏 =====

    /** 蒸馏结果：条数 + 是否走了 AI（false = 降级把纠正原话直接入库） */
    private record DistillResult(int count, boolean byAi) {
    }

    /** AI 蒸馏反馈为记忆条目；AI 不可用或解析失败时降级：纠正原话直接入 CORRECTION 记忆 */
    private DistillResult distill(Long userId, Long runId, List<AiFeedback> feedbacks, List<String> corrections) {
        String digest = buildDigest(feedbacks, corrections);
        try {
            String raw = aiChatService.complete("memory_distill", digest);
            List<Map<String, Object>> entries = parseMemoryArray(raw);
            for (Map<String, Object> e : entries) {
                String content = String.valueOf(e.getOrDefault("content", "")).trim();
                if (content.isEmpty()) {
                    continue;
                }
                memoryService.insert(userId, String.valueOf(e.getOrDefault("category", "STYLE")),
                        cap(content, 500), "DISTILL", runId);
            }
            return new DistillResult(entries.size(), true);
        } catch (Exception e) {
            log.warn("记忆蒸馏失败，降级为纠正原话入库：{}", e.getMessage());
            int n = 0;
            for (String c : corrections) {
                if (c == null || c.isBlank()) {
                    continue;
                }
                memoryService.insert(userId, "TONE", cap(c.trim(), 200), "CORRECTION", runId);
                if (++n >= 5) {
                    break;
                }
            }
            return new DistillResult(n, false);
        }
    }

    private String buildDigest(List<AiFeedback> feedbacks, List<String> corrections) {
        StringBuilder sb = new StringBuilder("本次运行反馈：\n");
        int i = 1;
        for (AiFeedback fb : feedbacks) {
            String excerpt = fb.getSuggestionText() == null ? "" : cap(fb.getSuggestionText().trim(), 160);
            String extra = fb.getEditDistance() == null ? "" : "，修改幅度 " + fb.getEditDistance() / 100.0 + "%";
            sb.append(i++).append(". [").append(fb.getVerdict()).append('/').append(fb.getSuggestionType())
              .append(extra).append("] ").append(excerpt).append('\n');
        }
        if (!corrections.isEmpty()) {
            sb.append("作者纠正（原话）：\n");
            for (String c : corrections) {
                if (c != null && !c.isBlank()) {
                    sb.append("- \"").append(cap(c.trim(), 120)).append("\"\n");
                }
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseMemoryArray(String raw) {
        try {
            int start = raw.indexOf('[');
            int end = raw.lastIndexOf(']');
            if (start < 0 || end <= start) {
                throw BizException.of(3206, "蒸馏输出格式异常");
            }
            JsonNode arr = objectMapper.readTree(raw.substring(start, end + 1));
            List<Map<String, Object>> out = new ArrayList<>();
            arr.forEach(n -> out.add((Map<String, Object>) objectMapper.convertValue(n, Map.class)));
            return out;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.of(3206, "蒸馏输出解析失败");
        }
    }

    // ===== 纯函数（单测覆盖） =====

    /** 归一化：换行/连续空白折叠为单空格（大小写保留——中文场景无意义），供包含判断与距离计算 */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r", "").replaceAll("\\s+", " ").trim();
    }

    /** 归一化编辑距离（万分比）：Levenshtein / 较长串长度，1.0 = 完全不同 */
    public static int levenshteinBp(String a, String b) {
        String x = cap(a, DISTANCE_CAP_CHARS);
        String y = cap(b, DISTANCE_CAP_CHARS);
        if (x.equals(y)) {
            return 0;
        }
        if (x.isEmpty()) {
            return 10000;
        }
        if (y.isEmpty()) {
            return 10000;
        }
        int[] prev = new int[y.length() + 1];
        int[] cur = new int[y.length() + 1];
        for (int j = 0; j <= y.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= x.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= y.length(); j++) {
                int cost = x.charAt(i - 1) == y.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        int max = Math.max(x.length(), y.length());
        return (int) Math.round(prev[y.length()] * 10000.0 / max);
    }

    public static Map<String, Object> buildMetrics(int suggestionsTotal, int applied, int verbatim,
                                                   int thumbsUp, int thumbsDown, int avgEditDistanceBp,
                                                   int corrections, int memoryLearned, boolean distilled) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("suggestionsTotal", suggestionsTotal);
        m.put("applied", applied);
        m.put("verbatim", verbatim);
        m.put("thumbsUp", thumbsUp);
        m.put("thumbsDown", thumbsDown);
        m.put("avgEditDistanceBp", avgEditDistanceBp);
        m.put("corrections", corrections);
        m.put("memoryLearned", memoryLearned);
        m.put("distilled", distilled);
        return m;
    }

    private String loadArticleContent(Long articleId) {
        if (articleId == null) {
            return null;
        }
        Article a = articleMapper.selectById(articleId);
        return a == null ? null : a.getContentMd();
    }

    private String toJson(Map<String, Object> metrics) {
        try {
            return objectMapper.writeValueAsString(metrics);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String cap(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 快照 JSON 里的 originRunId 可能是数字/字符串/null，安全收敛为 Long */
    private static Long asLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number num) {
            return num.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 记忆行给前端的只读视图（避免把实体直接暴露给 JSON 序列化的字段名漂移） */
    public record WritingMemoryLike(Long id, String category, String content, String source,
                                    String status, Long originRunId, LocalDateTime lastHitAt,
                                    LocalDateTime createdAt) {
    }
}
