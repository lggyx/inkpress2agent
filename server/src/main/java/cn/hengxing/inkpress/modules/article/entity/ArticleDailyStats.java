package cn.hengxing.inkpress.modules.article.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

@TableName("article_daily_stats")
public class ArticleDailyStats {

    private Long articleId;
    private LocalDate statDate;
    private Long views;
    private Long likes;
    private Long comments;

    public Long getArticleId() { return articleId; }
    public LocalDate getStatDate() { return statDate; }
    public Long getViews() { return views; }
    public Long getLikes() { return likes; }
    public Long getComments() { return comments; }

    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }
    public void setViews(Long views) { this.views = views; }
    public void setLikes(Long likes) { this.likes = likes; }
    public void setComments(Long comments) { this.comments = comments; }
}
