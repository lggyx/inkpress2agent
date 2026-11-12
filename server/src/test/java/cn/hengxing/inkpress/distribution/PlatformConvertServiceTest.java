package cn.hengxing.inkpress.distribution;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.article.support.MarkdownRenderer;
import cn.hengxing.inkpress.modules.distribution.service.PlatformConvertService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 分发平台排版转换：标题降级 / 公众号内联主题 / 非法平台拒绝 */
class PlatformConvertServiceTest {

    private final PlatformConvertService svc = new PlatformConvertService(new MarkdownRenderer());

    private final String md = "# 主标题\n\n## 小节\n\n正文段落。\n\n> 引用\n\n```java\ncode\n```\n";

    @Test
    void juejinStripsH1ToH2() {
        String html = svc.convert("juejin", md);
        assertTrue(html.contains("<h2>主标题</h2>"), "H1 应降级为 H2：" + html);
        assertTrue(!html.contains("<h1>"), "不应残留 H1");
        assertTrue(html.contains("<h2>小节</h2>"));
    }

    @Test
    void csdnStripsH1ToH2() {
        String html = svc.convert("csdn", md);
        assertTrue(html.contains("<h2>主标题</h2>"));
        assertTrue(!html.contains("<h1>"));
    }

    @Test
    void wechatAppliesInlineTheme() {
        String html = svc.convert("wechat", md);
        assertTrue(html.contains("border-left:4px solid #576b95"), "H2 应带公众号内联主题样式");
        assertTrue(html.contains("letter-spacing"), "段落应带内联排版样式");
        assertTrue(html.contains("<blockquote style="), "引用块应带内联样式");
    }

    @Test
    void unsupportedPlatformRejected() {
        BizException e = assertThrows(BizException.class, () -> svc.convert("xiaohongshu", md));
        assertTrue(String.valueOf(e.getMessage()).contains("不支持的平台"));
    }
}
