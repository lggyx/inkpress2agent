package cn.hengxing.inkpress.modules.distribution.service;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.distribution.entity.DistributionRecord;
import cn.hengxing.inkpress.modules.distribution.mapper.DistributionRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DistributionService {

    private final DistributionRecordMapper distributionRecordMapper;
    private final ArticleMapper articleMapper;

    public DistributionService(DistributionRecordMapper distributionRecordMapper, ArticleMapper articleMapper) {
        this.distributionRecordMapper = distributionRecordMapper;
        this.articleMapper = articleMapper;
    }

    @Transactional
    public Long create(Long articleId, String platform) {
        Article article = articleMapper.selectById(articleId);
        if (article == null || !"PUBLISHED".equals(article.getStatus()) || article.getDeleted() == 1) {
            throw BizException.of(2003, "文章不存在或未发布");
        }
        DistributionRecord existing = distributionRecordMapper.selectOne(new LambdaQueryWrapper<DistributionRecord>()
                .eq(DistributionRecord::getArticleId, articleId)
                .eq(DistributionRecord::getPlatform, platform)
                .in(DistributionRecord::getStatus, DistributionRecord.STATUS_PENDING, DistributionRecord.STATUS_FILLED));
        if (existing != null) {
            throw BizException.of(4001, "该平台已有进行中的分发任务");
        }
        DistributionRecord record = new DistributionRecord();
        record.setArticleId(articleId);
        record.setPlatform(platform);
        record.setStatus(DistributionRecord.STATUS_PENDING);
        record.setAttemptCount(0);
        distributionRecordMapper.insert(record);
        return record.getId();
    }

    /** agent 拉取待分发文章（含正文与目标平台） */
    public List<DistributionRecord> pending() {
        return distributionRecordMapper.selectList(new LambdaQueryWrapper<DistributionRecord>()
                .eq(DistributionRecord::getStatus, DistributionRecord.STATUS_PENDING)
                .orderByAsc(DistributionRecord::getId));
    }

    public Article articleOf(DistributionRecord record) {
        return articleMapper.selectById(record.getArticleId());
    }

    /** 草稿填充完成（agent 回调） */
    @Transactional
    public void markFilled(Long id, String draftUrl, String detail) {
        DistributionRecord record = mustGet(id);
        record.setStatus(DistributionRecord.STATUS_FILLED);
        record.setDraftUrl(draftUrl);
        record.setDetail(detail);
        record.setAttemptCount(record.getAttemptCount() + 1);
        distributionRecordMapper.updateById(record);
    }

    /** 发布完成回填外链（agent 回调，可带失败原因） */
    @Transactional
    public void report(Long id, boolean success, String publishedUrl, String detail) {
        DistributionRecord record = mustGet(id);
        record.setStatus(success ? DistributionRecord.STATUS_PUBLISHED : DistributionRecord.STATUS_FAILED);
        if (publishedUrl != null && !publishedUrl.isBlank()) {
            record.setPublishedUrl(publishedUrl);
        }
        record.setDetail(detail);
        distributionRecordMapper.updateById(record);
    }

    public List<DistributionRecord> listByArticle(Long articleId) {
        return distributionRecordMapper.selectList(new LambdaQueryWrapper<DistributionRecord>()
                .eq(DistributionRecord::getArticleId, articleId)
                .orderByDesc(DistributionRecord::getId));
    }

    private DistributionRecord mustGet(Long id) {
        DistributionRecord record = distributionRecordMapper.selectById(id);
        if (record == null) {
            throw BizException.of(4002, "分发记录不存在");
        }
        return record;
    }
}
