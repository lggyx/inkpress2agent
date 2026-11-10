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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AI 配置：system_config 键值管理 + 脱敏回显；提示词模板查询 */
@Service
public class AiConfigService {

    public static final String KEY_BASE_URL = "ai.base_url";
    public static final String KEY_API_KEY = "ai.api_key";
    public static final String KEY_MODEL = "ai.model";
    public static final String KEY_IMAGE_MODEL = "ai.image_model";

    private final SystemConfigMapper systemConfigMapper;
    private final PromptTemplateMapper promptTemplateMapper;

    public AiConfigService(SystemConfigMapper systemConfigMapper, PromptTemplateMapper promptTemplateMapper) {
        this.systemConfigMapper = systemConfigMapper;
        this.promptTemplateMapper = promptTemplateMapper;
    }

    public boolean isConfigured() {
        String key = get(KEY_API_KEY);
        String base = get(KEY_BASE_URL);
        return key != null && !key.isBlank() && base != null && !base.isBlank();
    }

    public String get(String key) {
        SystemConfig sc = systemConfigMapper.selectById(key);
        return sc == null ? null : sc.getConfigValue();
    }

    /** 管理端回显：密钥脱敏（前 6 后 4） */
    public Map<String, Object> view() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("baseUrl", get(KEY_BASE_URL));
        view.put("model", get(KEY_MODEL));
        view.put("imageModel", get(KEY_IMAGE_MODEL));
        view.put("apiKeyMasked", mask(get(KEY_API_KEY)));
        view.put("configured", isConfigured());
        return view;
    }

    @Transactional
    public void update(Long operatorId, String baseUrl, String model, String imageModel, String apiKey) {
        put(operatorId, KEY_BASE_URL, baseUrl);
        put(operatorId, KEY_MODEL, model);
        put(operatorId, KEY_IMAGE_MODEL, imageModel);
        // 传 null 表示不修改；传空串表示清除
        if (apiKey != null) {
            if (apiKey.isBlank()) {
                systemConfigMapper.deleteById(KEY_API_KEY);
            } else {
                put(operatorId, KEY_API_KEY, apiKey);
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
