package cn.hengxing.inkpress.modules.media;

import cn.hengxing.inkpress.modules.ai.service.AiConfigService;
import cn.hengxing.inkpress.modules.media.support.OssStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OSS 存储纯逻辑单测（Mockito）：配置完整性判定、对象 key 生成、公网链接拼接
 * （协议补全/尾斜杠清理）。S3 客户端为内部构建，网络路径不在单测范围。
 */
class OssStorageServiceTest {

    private AiConfigService configService;
    private OssStorageService service;
    private final Map<String, String> cfg = new HashMap<>();

    @BeforeEach
    void setUp() {
        configService = mock(AiConfigService.class);
        when(configService.get(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> cfg.get(inv.getArgument(0, String.class)));
        service = new OssStorageService(configService);
    }

    private void fillFullConfig() {
        cfg.put("oss.endpoint", "https://s3.example.com");
        cfg.put("oss.bucket", "inkpress");
        cfg.put("oss.access_key", "ak");
        cfg.put("oss.secret_key", "sk");
        cfg.put("oss.public_base_url", "https://cdn.example.com");
    }

    @Test
    void configuredRequiresAllFiveKeys() {
        assertFalse(service.configured());
        fillFullConfig();
        assertTrue(service.configured());
        cfg.remove("oss.secret_key");
        assertFalse(service.configured(), "缺任一密钥都视为未配置");
    }

    @Test
    void objectKeyFollowsMonthlyUuidPattern() {
        String withDot = service.objectKey(".png");
        assertTrue(withDot.matches("media/\\d{6}/[0-9a-f]{32}\\.png"), "实际 key：" + withDot);
        String withoutDot = service.objectKey("jpg");
        assertTrue(withoutDot.endsWith(".jpg"), "缺点号扩展名自动补点");
        String noExt = service.objectKey("");
        assertTrue(noExt.matches("media/\\d{6}/[0-9a-f]{32}"), "空扩展名不加后缀");
        assertFalse(service.objectKey(".png").equals(service.objectKey(".png")), "两次生成不同 key");
    }

    @Test
    void publicUrlNormalizesProtocolAndTrailingSlash() {
        cfg.put("oss.public_base_url", "https://cdn.example.com/");
        assertEquals("https://cdn.example.com/media/a.png", service.publicUrl("media/a.png"));

        cfg.put("oss.public_base_url", "cdn.example.com");
        assertEquals("http://cdn.example.com/media/a.png", service.publicUrl("media/a.png"),
                "用户漏填协议自动补 http://");

        cfg.put("oss.public_base_url", "http://cdn.example.com//");
        assertEquals("http://cdn.example.com/media/a.png", service.publicUrl("media/a.png"));
    }
}
