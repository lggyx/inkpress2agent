package cn.hengxing.inkpress.modules.article.controller;

import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** RSS 订阅（astro-pure 式页脚入口）：最新 20 篇已发布文章，公开可读 */
@RestController
public class RssController {

    // RFC-822 日期需带时区；publishedAt 为 LocalDateTime，固定东八区偏移输出
    private static final DateTimeFormatter RFC822 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss '+0800'", java.util.Locale.US);

    private final ArticleMapper articleMapper;
    private final String publicBase;

    public RssController(ArticleMapper articleMapper,
                         @org.springframework.beans.factory.annotation.Value(
                                 "${inkpress.mail.public-base-url:http://127.0.0.1:8912}") String publicBase) {
        this.articleMapper = articleMapper;
        this.publicBase = publicBase.endsWith("/") ? publicBase.substring(0, publicBase.length() - 1) : publicBase;
    }

    @GetMapping(value = "/rss.xml", produces = "application/rss+xml;charset=UTF-8")
    @ResponseBody
    public String rss() {
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "PUBLISHED")
                .eq(Article::getDeleted, 0)
                .orderByDesc(Article::getPublishedAt)
                .last("LIMIT 20"));
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<rss version=\"2.0\"><channel>\n")
                .append("<title>InkPress</title>\n")
                .append("<link>").append(esc(publicBase)).append("</link>\n")
                .append("<description>InkPress 个人博客与文章管理系统</description>\n");
        for (Article a : articles) {
            String url = publicBase + "/article/" + a.getId();
            sb.append("<item>\n")
                    .append("<title>").append(esc(a.getTitle())).append("</title>\n")
                    .append("<link>").append(esc(url)).append("</link>\n")
                    .append("<guid>").append(esc(url)).append("</guid>\n")
                    .append("<pubDate>").append(a.getPublishedAt() == null ? ""
                            : RFC822.format(a.getPublishedAt())).append("</pubDate>\n")
                    .append("<description>").append(esc(nvl(a.getSeoDescription(), a.getGeoSummary()))).append("</description>\n")
                    .append("</item>\n");
        }
        sb.append("</channel></rss>");
        return sb.toString();
    }

    private static String nvl(String a, String b) {
        return a == null || a.isBlank() ? (b == null ? "" : b) : a;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
