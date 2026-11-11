package cn.hengxing.inkpress.modules.ai.service;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.ai.entity.PromptTemplate;
import cn.hengxing.inkpress.modules.ai.entity.SystemConfig;
import cn.hengxing.inkpress.modules.ai.mapper.PromptTemplateMapper;
import cn.hengxing.inkpress.modules.ai.mapper.SystemConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 配置（按能力分档）：text 文本对话 / vision 视觉理解 / image 图像生成 / embedding 向量。
 * 每个能力独立 provider（协议 + BaseURL + Key + 模型）；未配置的能力槽回退到旧全局 ai.* 键。
 */
@Service
public class AiConfigService {

    public static final String CAP_TEXT = "text";
    public static final String CAP_VISION = "vision";
    public static final String CAP_IMAGE = "image";
    public static final String CAP_EMBEDDING = "embedding";
    public static final List<String> CAPABILITIES = List.of(CAP_TEXT, CAP_VISION, CAP_IMAGE, CAP_EMBEDDING);

    public static final String PROTOCOL_OPENAI = "openai";
    public static final String PROTOCOL_ANTHROPIC = "anthropic";

    // 旧全局键（作为能力槽的回退配置来源）
    public static final String KEY_BASE_URL = "ai.base_url";
    public static final String KEY_API_KEY = "ai.api_key";
    public static final String KEY_MODEL = "ai.model";
    public static final String KEY_IMAGE_MODEL = "ai.image_model";
    public static final String KEY_EMBEDDING_MODEL = "ai.embedding_model";
    public static final String KEY_PROTOCOL = "ai.protocol";

    private final SystemConfigMapper systemConfigMapper;
    private final PromptTemplateMapper promptTemplateMapper;

    public AiConfigService(SystemConfigMapper systemConfigMapper, PromptTemplateMapper promptTemplateMapper) {
        this.systemConfigMapper = systemConfigMapper;
        this.promptTemplateMapper = promptTemplateMapper;
    }

    /** 能力级 provider：协议 + BaseURL + Key + 模型（槽位缺省时回退旧全局键） */
    public record AiProvider(String capability, String protocol, String baseUrl, String apiKey, String model) {
    }

    public AiProvider provider(String capability) {
        String protocol = get("ai." + capability + ".protocol");
        if (protocol == null || protocol.isBlank()) {
            protocol = get(KEY_PROTOCOL);
        }
        boolean anthropic = PROTOCOL_ANTHROPIC.equals(protocol);
        String baseUrl = firstNonBlank(get("ai." + capability + ".base_url"), get(KEY_BASE_URL));
        String apiKey = firstNonBlank(get("ai." + capability + ".api_key"), get(KEY_API_KEY));
        String model = firstNonBlank(
                get("ai." + capability + ".model"),
                legacyModel(capability),
                defaultModel(capability));
        return new AiProvider(capability, anthropic ? PROTOCOL_ANTHROPIC : PROTOCOL_OPENAI, baseUrl, apiKey, model);
    }

    /** 协议无关的能力默认模型（仅作占位提示，真正可用需用户配置） */
    private String defaultModel(String capability) {
        return switch (capability) {
            case CAP_EMBEDDING -> "embedding-3";
            case CAP_IMAGE -> "cogview-3-flash";
            case CAP_VISION -> "glm-4v";
            default -> "glm-4-flash";
        };
    }

    private String legacyModel(String capability) {
        return switch (capability) {
            case CAP_IMAGE -> get(KEY_IMAGE_MODEL);
            case CAP_EMBEDDING -> get(KEY_EMBEDDING_MODEL);
            default -> get(KEY_MODEL);
        };
    }

    private String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return candidates.length > 0 ? candidates[candidates.length - 1] : null;
    }

    public boolean configured(String capability) {
        AiProvider p = provider(capability);
        return p.baseUrl() != null && !p.baseUrl().isBlank() && p.apiKey() != null && !p.apiKey().isBlank();
    }

    /** 兼容旧调用点：文本能力是否可用 */
    public boolean isConfigured() {
        return configured(CAP_TEXT);
    }

    public String get(String key) {
        SystemConfig sc = systemConfigMapper.selectById(key);
        return sc == null ? null : sc.getConfigValue();
    }

    /** 管理端视图：四个能力槽分组回显，密钥脱敏；向量槽附维度 */
    public Map<String, Object> view() {
        Map<String, Object> caps = new LinkedHashMap<>();
        for (String cap : CAPABILITIES) {
            AiProvider p = provider(cap);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("protocol", p.protocol());
            item.put("baseUrl", p.baseUrl());
            item.put("model", p.model());
            item.put("apiKeyMasked", mask(p.apiKey()));
            item.put("configured", configured(cap));
            if (CAP_EMBEDDING.equals(cap)) {
                String d = get("ai.embedding.dimensions");
                item.put("dimensions", d == null || d.isBlank() ? "1024" : d);
            }
            caps.put(cap, item);
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("capabilities", caps);
        return view;
    }

    /** 批量更新各能力槽；apiKey 约定：null 不修改、空串清除 */
    @Transactional
    public void update(Long operatorId, List<Map<String, String>> updates) {
        for (Map<String, String> u : updates) {
            String cap = u.get("capability");
            if (cap == null || !CAPABILITIES.contains(cap)) {
                throw BizException.of(3007, "未知能力槽：" + cap);
            }
            put(operatorId, "ai." + cap + ".protocol", u.get("protocol"));
            put(operatorId, "ai." + cap + ".base_url", u.get("baseUrl"));
            put(operatorId, "ai." + cap + ".model", u.get("model"));
            String apiKey = u.get("apiKey");
            if (apiKey != null) {
                if (apiKey.isBlank()) {
                    systemConfigMapper.deleteById("ai." + cap + ".api_key");
                } else {
                    put(operatorId, "ai." + cap + ".api_key", apiKey);
                }
            }
            // 向量槽专属：维度（默认 1024，需与 ES 索引 dense_vector 一致）
            if (CAP_EMBEDDING.equals(cap) && u.get("dimensions") != null && !u.get("dimensions").isBlank()) {
                put(operatorId, "ai.embedding.dimensions", u.get("dimensions").trim());
            }
        }
    }

    public List<PromptTemplate> templates() {
        return promptTemplateMapper.selectList(new LambdaQueryWrapper<PromptTemplate>()
                .orderByAsc(PromptTemplate::getId));
    }

    @Transactional
    public void updateTemplate(Long operatorId, String category, String name, String content, boolean enabled) {
        PromptTemplate t = promptTemplateMapper.selectOne(
                new LambdaQueryWrapper<PromptTemplate>().eq(PromptTemplate::getCategory, category));
        if (t == null) {
            t = new PromptTemplate();
            t.setCategory(category);
        }
        t.setName(name);
        t.setContent(content);
        t.setEnabled(enabled ? 1 : 0);
        t.setUpdatedBy(operatorId);
        t.setUpdatedAt(LocalDateTime.now());
        if (t.getId() == null) {
            promptTemplateMapper.insert(t);
        } else {
            promptTemplateMapper.updateById(t);
        }
    }

    public PromptTemplate mustTemplate(String category) {
        PromptTemplate t = promptTemplateMapper.selectOne(
                new LambdaQueryWrapper<PromptTemplate>().eq(PromptTemplate::getCategory, category));
        if (t == null || t.getEnabled() != 1) {
            throw BizException.of(3001, "提示词模板不可用：" + category);
        }
        return t;
    }

    public String render(String category, String userContent) {
        return mustTemplate(category).getContent().replace("{{content}}", userContent == null ? "" : userContent);
    }

    private void put(Long operatorId, String key, String value) {
        if (value == null) {
            return;
        }
        SystemConfig sc = new SystemConfig();
        sc.setConfigKey(key);
        sc.setConfigValue(value);
        sc.setUpdatedBy(operatorId);
        if (systemConfigMapper.selectById(key) == null) {
            systemConfigMapper.insert(sc);
        } else {
            systemConfigMapper.updateById(sc);
        }
    }

    private String mask(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.length() <= 10) {
            return key.charAt(0) + "****";
        }
        return key.substring(0, 6) + "****" + key.substring(key.length() - 4);
    }
}
