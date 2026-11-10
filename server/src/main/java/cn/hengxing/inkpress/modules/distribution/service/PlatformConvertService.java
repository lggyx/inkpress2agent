package cn.hengxing.inkpress.modules.distribution.service;

import cn.hengxing.inkpress.modules.article.support.MarkdownRenderer;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Markdown → 平台 HTML 深度排版转换：
 * 公共管线（CommonMark）→ 平台规则（标题降级/代码高亮/内联样式主题）。
 */
@Service
public class PlatformConvertService {

    private static final Set<String> SUPPORTED = Set.of("juejin", "csdn", "zhihu", "wechat");

    private final MarkdownRenderer markdownRenderer;

    public PlatformConvertService(MarkdownRenderer markdownRenderer) {
        this.markdownRenderer = markdownRenderer;
    }

    public Set<String> supported() {
        return SUPPORTED;
    }

    public String convert(String platform, String contentMd) {
        String html = markdownRenderer.render(contentMd);
        return switch (platform) {
            case "juejin" -> stripH1(html);
            case "csdn" -> stripH1(html);
            case "zhihu" -> zhihu(html);
            case "wechat" -> wechatTheme(html);
            default -> throw new cn.hengxing.inkpress.common.BizException(4003, "不支持的平台：" + platform);
        };
    }

    /** 掘金/CSDN 编辑器自带标题栏，正文 H1 会与标题重复 */
    private String stripH1(String html) {
        return html.replaceAll("<h1>(.*?)</h1>", "<h2>$1</h2>");
    }

    /** 知乎：H1 降级为 H2（知乎无 H1 层级），外链保留 */
    private String zhihu(String html) {
        return stripH1(html);
    }

    /** 公众号深度主题：内联样式（微信编辑器只吃 inline style） */
    private String wechatTheme(String html) {
        String out = html;
        out = out.replaceAll("<h2>(.*?)</h2>",
                "<h2 style=\"margin:24px 0 12px;padding-left:10px;border-left:4px solid #576b95;"
                        + "font-size:18px;font-weight:600;color:#3f4a5f;line-height:1.4;\">$1</h2>");
        out = out.replaceAll("<h3>(.*?)</h3>",
                "<h3 style=\"margin:20px 0 10px;font-size:16px;font-weight:600;color:#3f4a5f;\">$1</h3>");
        out = out.replaceAll("<blockquote>",
                "<blockquote style=\"margin:12px 0;padding:10px 14px;background:#f7f8fa;"
                        + "border-left:3px solid #b2b2b2;color:#666;font-size:14px;\">");
        out = out.replaceAll("<p>", "<p style=\"margin:14px 0;font-size:15px;color:#3f3f3f;line-height:1.75;letter-spacing:.3px;\">");
        out = out.replaceAll("<pre>", "<pre style=\"margin:14px 0;padding:12px;background:#f6f8fa;"
                + "border-radius:6px;overflow-x:auto;font-size:13px;line-height:1.6;\">");
        out = out.replaceAll("<code>", "<code style=\"background:#f1f1f1;padding:2px 5px;border-radius:3px;"
                + "font-size:13px;color:#c7254e;\">");
        out = out.replaceAll("<a ", "<a style=\"color:#576b95;text-decoration:none;\" ");
        out = out.replaceAll("<img ", "<img style=\"max-width:100%;border-radius:4px;display:block;margin:10px auto;\" ");
        out = out.replaceAll("<ul>", "<ul style=\"padding-left:22px;margin:12px 0;\">");
        out = out.replaceAll("<ol>", "<ol style=\"padding-left:22px;margin:12px 0;\">");
        out = out.replaceAll("<li>", "<li style=\"margin:6px 0;font-size:15px;color:#3f3f3f;line-height:1.7;\">");
        return "<section style=\"font-family:-apple-system,BlinkMacSystemFont,'Helvetica Neue',Helvetica,"
                + "'Segoe UI',Arial,'PingFang SC','Hiragino Sans GB','Microsoft YaHei',sans-serif;\">" + out + "</section>";
    }
}
