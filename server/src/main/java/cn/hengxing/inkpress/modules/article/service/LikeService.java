package cn.hengxing.inkpress.modules.article.service;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.entity.ArticleLike;
import cn.hengxing.inkpress.modules.article.mapper.ArticleLikeMapper;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class LikeService {

    private final ArticleLikeMapper articleLikeMapper;
    private final ArticleMapper articleMapper;

    public LikeService(ArticleLikeMapper articleLikeMapper, ArticleMapper articleMapper) {
        this.articleLikeMapper = articleLikeMapper;
        this.articleMapper = articleMapper;
    }

    /** 点赞/取消点赞（登录用户，article_like 主键去重）；返回当前状态 */
    @Transactional
    public Map<String, Object> toggle(Long articleId, Long userId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null || !"PUBLISHED".equals(article.getStatus()) || article.getDeleted() == 1) {
            throw BizException.of(2003, "文章不存在或未发布");
        }
        ArticleLike existing = articleLikeMapper.selectOne(new LambdaQueryWrapper<ArticleLike>()
                .eq(ArticleLike::getArticleId, articleId).eq(ArticleLike::getUserId, userId));
        boolean liked;
        if (existing != null) {
            articleLikeMapper.deleteById(existing);
            articleMapper.update(null, new UpdateWrapper<Article>()
                    .eq("id", articleId).setSql("like_count = GREATEST(like_count - 1, 0)"));
            liked = false;
        } else {
            try {
                articleLikeMapper.insert(new ArticleLike(articleId, userId));
            } catch (DuplicateKeyException e) {
                // 并发重复点赞幂等：视为已赞
            }
            articleMapper.update(null, new UpdateWrapper<Article>()
                    .eq("id", articleId).setSql("like_count = like_count + 1"));
            liked = true;
        }
        return Map.of("liked", liked);
    }
}
