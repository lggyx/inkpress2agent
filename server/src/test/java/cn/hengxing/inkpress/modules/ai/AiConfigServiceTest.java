package cn.hengxing.inkpress.modules.ai;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.ai.entity.PromptTemplate;
import cn.hengxing.inkpress.modules.ai.entity.SystemConfig;
import cn.hengxing.inkpress.modules.ai.mapper.PromptTemplateMapper;
import cn.hengxing.inkpress.modules.ai.mapper.SystemConfigMapper;
import cn.hengxing.inkpress.modules.ai.service.AiConfigService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 配置编排层单测（Mockito）：四槽 provider 解析的回退链（槽位→旧全局键→默认模型）、
 * 配置完整性判定、管理端脱敏视图、批量更新的 null/空串/维度语义。
 */
class AiConfigServiceTest {

    private SystemConfigMapper systemConfigMapper;
    private PromptTemplateMapper promptTemplateMapper;
    private AiConfigService service;
    private final Map<String, String> cfg = new HashMap<>();

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, PromptTemplate.class);
    }

    @BeforeEach
    void setUp() {
        systemConfigMapper = mock(SystemConfigMapper.class);
        promptTemplateMapper = mock(PromptTemplateMapper.class);
        service = new AiConfigService(systemConfigMapper, promptTemplateMapper);
        when(systemConfigMapper.selectById(any()))
                .thenAnswer(inv -> {
                    String key = inv.getArgument(0);
                    String v = cfg.get(key);
                    if (v == null) {
                        return null;
                    }
                    SystemConfig sc = new SystemConfig();
                    sc.setConfigKey(key);
                    sc.setConfigValue(v);
                    return sc;
                });
    }

    @Test
    void providerFallsBackToLegacyKeysThenDefaultModel() {
        AiProviderLike legacyOnly = snapshot("embedding");
        assertNull(legacyOnly.baseUrl);
        assertEquals("embedding-3", legacyOnly.model, "槽位/旧键都缺时回退协议无关默认模型");

        cfg.put("ai.base_url", "https://legacy.example.com");
        cfg.put("ai.api_key", "legacy-key");
        cfg.put("ai.embedding_model", "bge-m3");

        AiProviderLike p = snapshot("embedding");
        assertEquals("https://legacy.example.com", p.baseUrl, "槽位缺省回退旧全局键");
        assertEquals("legacy-key", p.apiKey);
        assertEquals("bge-m3", p.model, "旧键模型优先于默认模型");
        assertEquals("openai", p.protocol, "协议缺省为 OpenAI 兼容");
    }

    @Test
    void providerSlotConfigOverridesLegacy() {
        cfg.put("ai.base_url", "https://legacy.example.com");
        cfg.put("ai.api_key", "legacy-key");
        cfg.put("ai.text.base_url", "https://slot.example.com");
        cfg.put("ai.text.api_key", "slot-key");
        cfg.put("ai.text.protocol", "anthropic");

        AiProviderLike p = snapshot("text");
        assertEquals("https://slot.example.com", p.baseUrl, "槽位配置优先于旧全局键");
        assertEquals("slot-key", p.apiKey);
        assertEquals("anthropic", p.protocol);
        assertEquals("glm-4-flash", p.model, "text 槽默认模型");
    }

    @Test
    void configuredRequiresBaseUrlAndKeyTogether() {
        cfg.put("ai.text.api_key", "only-key");
        assertFalse(service.configured("text"));
        assertFalse(service.isConfigured());
        cfg.put("ai.text.base_url", "https://x.example.com");
        assertTrue(service.configured("text"));
    }

    @Test
    void viewMasksApiKeyAndDefaultsDimensions() {
        cfg.put("ai.text.base_url", "https://x.example.com");
        cfg.put("ai.text.api_key", "sk-abcdef1234567890wxyz");

        Map<String, Object> view = service.view();
        @SuppressWarnings("unchecked")
        Map<String, Object> text = (Map<String, Object>) view.get("capabilities");
        @SuppressWarnings("unchecked")
        Map<String, Object> textSlot = (Map<String, Object>) text.get("text");
        assertEquals("sk-abc****wxyz", textSlot.get("apiKeyMasked"), "前 6 后 4 脱敏");
        assertTrue((Boolean) textSlot.get("configured"));

        @SuppressWarnings("unchecked")
        Map<String, Object> embSlot = (Map<String, Object>) text.get("embedding");
        assertEquals("1024", embSlot.get("dimensions"), "维度缺省显示 1024");
    }

    @Test
    void updateEmptyKeyClearsWhileNullKeeps() {
        cfg.put("ai.text.api_key", "old-key");

        service.update(1L, List.of(Map.of("capability", "text", "apiKey", "")));
        // MP 的 deleteById 有 Serializable/T 两个重载，显式窄化到 Serializable 键删除
        verify(systemConfigMapper).deleteById((java.io.Serializable) "ai.text.api_key");

        // null = 不修改（Map.of 不允许 null 值，用 HashMap 构造）
        Map<String, String> keep = new HashMap<>();
        keep.put("capability", "text");
        keep.put("apiKey", null);
        service.update(1L, List.of(keep));
        verify(systemConfigMapper, org.mockito.Mockito.times(1)).deleteById(org.mockito.ArgumentMatchers.<java.io.Serializable>any());
    }

    @Test
    void updateUnknownCapabilityRejected() {
        BizException e = assertThrows(BizException.class,
                () -> service.update(1L, List.of(Map.of("capability", "video"))));
        assertEquals(3007, e.getCode());
    }

    @Test
    void updateEmbeddingDimensionsPersisted() {
        service.update(1L, List.of(Map.of("capability", "embedding", "dimensions", " 2048 ")));
        ArgumentCaptor<SystemConfig> cap = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigMapper).insert(cap.capture());
        assertEquals("ai.embedding.dimensions", cap.getValue().getConfigKey());
        assertEquals("2048", cap.getValue().getConfigValue(), "维度值应去空白");
    }

    @Test
    void renderSubstitutesContentPlaceholder() {
        PromptTemplate t = new PromptTemplate();
        t.setCategory("summary");
        t.setName("摘要模板");
        t.setContent("请为以下内容生成摘要：{{content}}");
        t.setEnabled(1);
        when(promptTemplateMapper.selectOne(any())).thenReturn(t);

        assertEquals("请为以下内容生成摘要：正文", service.render("summary", "正文"));
    }

    @Test
    void mustTemplateRejectsMissingOrDisabled() {
        when(promptTemplateMapper.selectOne(any())).thenReturn(null);
        assertEquals(3001, assertThrows(BizException.class,
                () -> service.mustTemplate("review")).getCode());

        PromptTemplate disabled = new PromptTemplate();
        disabled.setCategory("review");
        disabled.setEnabled(0);
        when(promptTemplateMapper.selectOne(any())).thenReturn(disabled);
        assertEquals(3001, assertThrows(BizException.class,
                () -> service.mustTemplate("review")).getCode());
    }

    /** 只抽取测试关心的 provider 字段（避免与主类 record 同名冲突） */
    private record AiProviderLike(String protocol, String baseUrl, String apiKey, String model) {
    }

    private AiProviderLike snapshot(String capability) {
        AiConfigService.AiProvider p = service.provider(capability);
        return new AiProviderLike(p.protocol(), p.baseUrl(), p.apiKey(), p.model());
    }
}
