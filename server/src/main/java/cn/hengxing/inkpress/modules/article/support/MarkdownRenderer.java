package cn.hengxing.inkpress.modules.article.support;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

/** Markdown → HTML（正文渲染与分发转换共用同一渲染管线） */
@Component
public class MarkdownRenderer {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    public String render(String markdown) {
        Node doc = parser.parse(markdown == null ? "" : markdown);
        return renderer.render(doc);
    }
}
