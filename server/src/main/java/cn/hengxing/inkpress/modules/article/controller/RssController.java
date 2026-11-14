package cn.hengxing.inkpress.modules.article.controller;

import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
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
    private final String configuredBase;

    public RssController(ArticleMapper articleMapper,
                         @org.springframework.beans.factory.annotation.Value(
                                 "${inkpress.mail.public-base-url:http://127.0.0.1:8912}") String publicBase) {
        this.articleMapper = articleMapper;
        this.configuredBase = publicBase.endsWith("/") ? publicBase.substring(0, publicBase.length() - 1) : publicBase;
    }

    /**
     * 动态解析站点 origin：优先 X-Forwarded-Host/Proto（经 nginx 反代），
     * 退化到请求 Host/Scheme，最后才用配置值——与 sitemap/AI 线同款域名自适应。
     */
    private String resolveBase(HttpServletRequest request) {
        String host = firstNonBlank(request.getHeader("X-Forwarded-Host"), request.getHeader("Host"));
        if (host == null || host.isBlank()) {
            return configuredBase;
        }
        String proto = firstNonBlank(request.getHeader("X-Forwarded-Proto"), request.getScheme());
        return proto + "://" + host;
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    @GetMapping(value = "/rss.xml", produces = "application/rss+xml;charset=UTF-8")
    @ResponseBody
    public String rss(HttpServletRequest request) {
        String publicBase = resolveBase(request);
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
