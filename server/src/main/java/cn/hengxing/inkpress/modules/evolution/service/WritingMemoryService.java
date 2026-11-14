package cn.hengxing.inkpress.modules.evolution.service;

import cn.hengxing.inkpress.modules.evolution.entity.WritingMemory;
import cn.hengxing.inkpress.modules.evolution.mapper.WritingMemoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 写作记忆存取：进化闭环的"学到的东西"落库与检索。
 * 独立于 AiChatService（只依赖 Mapper），供其注入 system 提示，避免循环依赖。
 */
@Service
public class WritingMemoryService {

    /** 注入 AI 提示的最大记忆条数（控制 token 预算） */
    public static final int INJECT_LIMIT = 12;

    private final WritingMemoryMapper memoryMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WritingMemoryService(WritingMemoryMapper memoryMapper) {
        this.memoryMapper = memoryMapper;
    }

    /** 作者的 ACTIVE 记忆，新学的在前 */
    public List<WritingMemory> activeList(Long userId) {
        return memoryMapper.selectList(new LambdaQueryWrapper<WritingMemory>()
                .eq(WritingMemory::getUserId, userId)
                .eq(WritingMemory::getStatus, "ACTIVE")
                .orderByDesc(WritingMemory::getCreatedAt)
                .last("LIMIT " + INJECT_LIMIT));
    }

    /**
     * 组装注入 system 提示的「写作偏好记忆」块；无记忆返回 null（AI 行为与现状一致）。
     * 命中的记忆更新 last_hit_at——面板可据此展示"本次运行用了哪些记忆"。
     */
    public String activeBlock(Long userId) {
        List<WritingMemory> memories = activeList(userId);
        if (memories.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("【作者写作偏好记忆】以下是从该作者历史反馈中沉淀的偏好，必须严格遵守：\n");
        LocalDateTime now = LocalDateTime.now();
        for (WritingMemory m : memories) {
            sb.append("- [").append(m.getCategory()).append("] ").append(m.getContent()).append('\n');
            m.setLastHitAt(now);
            memoryMapper.updateById(m);
        }
        return sb.toString().trim();
    }

    public WritingMemory insert(Long userId, String category, String content, String source, Long originRunId) {
        WritingMemory m = new WritingMemory();
        m.setUserId(userId);
        m.setCategory(safeCategory(category));
        m.setContent(content);
        m.setSource(source);
        m.setStatus("ACTIVE");
        m.setOriginRunId(originRunId);
        memoryMapper.insert(m);
        return m;
    }

    /** 回退第一步：当前 ACTIVE 全部置 SUPERSEDED（幂等：无 ACTIVE 时为空操作） */
    public int supersedeAllActive(Long userId) {
        List<WritingMemory> actives = memoryMapper.selectList(new LambdaQueryWrapper<WritingMemory>()
                .eq(WritingMemory::getUserId, userId)
                .eq(WritingMemory::getStatus, "ACTIVE"));
        for (WritingMemory m : actives) {
            m.setStatus("SUPERSEDED");
            memoryMapper.updateById(m);
        }
        return actives.size();
    }

    /** 快照 JSON：当前 ACTIVE 记忆的最小字段集（id/category/content/source/originRunId），
     *  不序列化实体（LocalDateTime 需 JSR310 模块，裸 ObjectMapper 会炸） */
    public String snapshotJson(Long userId) {
        List<Map<String, Object>> minimal = new java.util.ArrayList<>();
        for (WritingMemory m : activeListAll(userId)) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("category", m.getCategory());
            row.put("content", m.getContent());
            row.put("source", m.getSource());
            row.put("originRunId", m.getOriginRunId());
            minimal.add(row);
        }
        try {
            return objectMapper.writeValueAsString(minimal);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<WritingMemory> activeListAll(Long userId) {
        return memoryMapper.selectList(new LambdaQueryWrapper<WritingMemory>()
                .eq(WritingMemory::getUserId, userId)
                .eq(WritingMemory::getStatus, "ACTIVE")
                .orderByDesc(WritingMemory::getCreatedAt));
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> parseSnapshot(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, List.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String safeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "STYLE";
        }
        return switch (category.toUpperCase()) {
            case "STYLE", "TONE", "STRUCTURE", "LENGTH", "FACT" -> category.toUpperCase();
            default -> "STYLE";
        };
    }
}
