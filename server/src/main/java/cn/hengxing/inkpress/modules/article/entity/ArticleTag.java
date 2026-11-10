package cn.hengxing.inkpress.modules.article.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("article_tag")
public class ArticleTag {

    private Long articleId;
    private Long tagId;

    public ArticleTag() {
    }

    public ArticleTag(Long articleId, Long tagId) {
        this.articleId = articleId;
        this.tagId = tagId;
    }

    public Long getArticleId() { return articleId; }
    public Long getTagId() { return tagId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }
}
