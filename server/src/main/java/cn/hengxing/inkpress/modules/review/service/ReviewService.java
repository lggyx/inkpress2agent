package cn.hengxing.inkpress.modules.review.service;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.ai.service.AiChatService;
import cn.hengxing.inkpress.modules.ai.service.AiConfigService;
import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.review.entity.ReviewReport;
import cn.hengxing.inkpress.modules.review.mapper.ReviewReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** M9 内容评审：AI 多维评分报告落库 + 低分发布闸口判定 */
@Service
public class ReviewService {

    public static final int LOW_SCORE_THRESHOLD = 60;

    private static final Pattern P_READ = Pattern.compile("可读性[:：]\\s*(\\d+)");
    private static final Pattern P_STRUCT = Pattern.compile("结构完整性[:：]\\s*(\\d+)");
    private static final Pattern P_KEYWORD = Pattern.compile("关键词密度[:：]\\s*(\\d+)");
    private static final Pattern P_SEO = Pattern.compile("SEO 友好度[:：]\\s*(\\d+)");
    private static final Pattern P_TOTAL = Pattern.compile("总分[:：]\\s*(\\d+)");

    private final ArticleMapper articleMapper;
    private final ReviewReportMapper reviewReportMapper;
    private final AiChatService aiChatService;
    private final AiConfigService aiConfigService;

    public ReviewService(ArticleMapper articleMapper, ReviewReportMapper reviewReportMapper,
                         AiChatService aiChatService, AiConfigService aiConfigService) {
        this.articleMapper = articleMapper;
        this.reviewReportMapper = reviewReportMapper;
        this.aiChatService = aiChatService;
        this.aiConfigService = aiConfigService;
    }

    /** 触发评审：手动或发布前自动；AI 未配置时报错提示 */
    public ReviewReport run(Long articleId, String triggerType) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw BizException.of(2003, "文章不存在");
        }
        if (!aiConfigService.isConfigured()) {
            throw BizException.of(3004, "AI 服务未配置，无法评审");
        }
        String raw = aiChatService.complete("review", article.getContentMd());
        ReviewReport report = new ReviewReport();
        report.setArticleId(articleId);
        report.setTriggerType(triggerType);
        report.setModel(aiConfigService.get(AiConfigService.KEY_MODEL));
        report.setReadability(score(P_READ, raw));
        report.setStructure(score(P_STRUCT, raw));
        report.setKeywordDensity(score(P_KEYWORD, raw));
        report.setSeo(score(P_SEO, raw));
        report.setTotalScore(score(P_TOTAL, raw));
        report.setSuggestion(raw);
        // 关键分数缺失视为低置信，闸口不采纳
        report.setConfidence(report.getTotalScore() != null ? "HIGH" : "LOW");
        reviewReportMapper.insert(report);
        return report;
    }

    /** 最新一份可用于闸口的评审总分；无有效报告返回 null */
    public Integer latestScore(Long articleId) {
        ReviewReport latest = reviewReportMapper.selectOne(new LambdaQueryWrapper<ReviewReport>()
                .eq(ReviewReport::getArticleId, articleId)
                .eq(ReviewReport::getConfidence, "HIGH")
                .orderByDesc(ReviewReport::getId)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getTotalScore();
    }

    public ReviewReport latest(Long articleId) {
        return reviewReportMapper.selectOne(new LambdaQueryWrapper<ReviewReport>()
                .eq(ReviewReport::getArticleId, articleId)
                .orderByDesc(ReviewReport::getId)
                .last("LIMIT 1"));
    }

    private Integer score(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }
}
