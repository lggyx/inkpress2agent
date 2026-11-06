package com.inkpress.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inkpress.config.AiProperties;
import com.inkpress.dto.AiConfigDTO;
import com.inkpress.entity.AiConfig;
import com.inkpress.event.AiConfigChangedEvent;
import com.inkpress.mapper.AiConfigMapper;
import com.inkpress.service.AiConfigService;
import com.inkpress.vo.AiConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * AI 配置管理实现。
 * 设计要点：
 * 1. 单行配置（id=1），有行即数据库配置优先，无行回落环境变量；
 * 2. getEffective() 每次实时查库，管理端保存后无需重启即时生效；
 * 3. 密钥只在服务端流转，VO 脱敏回显；
 * 4. 向量化相关配置变化时发事件，由向量服务异步重建索引。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigServiceImpl implements AiConfigService {

    /** 单行配置固定主键 */
    private static final int SINGLETON_ID = 1;

    private final AiConfigMapper aiConfigMapper;
    private final AiProperties envProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public AiProperties getEffective() {
        AiConfig row = loadRow();
        if (row == null) {
            return envProperties;
        }
        AiProperties p = new AiProperties();
        p.setBaseUrl(row.getBaseUrl());
        p.setApiKey(row.getApiKey());
        p.setModel(row.getModel());
        p.setTemperature(row.getTemperature() == null ? 0.7 : row.getTemperature().doubleValue());
        p.setTimeoutSeconds(row.getTimeoutSeconds() == null ? 60 : row.getTimeoutSeconds());
        AiProperties.Embedding embedding = new AiProperties.Embedding();
        embedding.setProvider(StringUtils.hasText(row.getEmbeddingProvider())
                ? row.getEmbeddingProvider() : "auto");
        embedding.setModel(StringUtils.hasText(row.getEmbeddingModel())
                ? row.getEmbeddingModel() : "embedding-3");
        embedding.setLocalDim(row.getLocalDim() == null ? 512 : row.getLocalDim());
        p.setEmbedding(embedding);
        return p;
    }

    @Override
    public AiConfigVO getForAdmin() {
        AiConfig row = loadRow();
        AiProperties effective = getEffective();
        AiConfigVO vo = new AiConfigVO();
        vo.setSource(row == null ? "env" : "db");
        vo.setBaseUrl(effective.getBaseUrl());
        vo.setApiKeyMasked(mask(effective.getApiKey()));
        vo.setHasApiKey(effective.enabled());
        vo.setModel(effective.getModel());
        vo.setTemperature(effective.getTemperature());
        vo.setTimeoutSeconds(effective.getTimeoutSeconds());
        vo.setEmbeddingProvider(effective.getEmbedding().getProvider());
        vo.setEmbeddingModel(effective.getEmbedding().getModel());
        vo.setLocalDim(effective.getEmbedding().getLocalDim());
        vo.setEmbeddingImpl(implLabel(effective));
        if (row != null) {
            vo.setUpdatedBy(row.getUpdatedBy());
            vo.setUpdatedAt(row.getUpdatedAt());
        }
        return vo;
    }

    @Override
    @Transactional
    public void update(AiConfigDTO dto, String operator) {
        String before = embeddingSignature(getEffective());

        // 密钥语义：null=保持当前生效密钥不变；空串=清除；其他=替换
        String apiKey;
        if (dto.getApiKey() == null) {
            apiKey = getEffective().getApiKey() == null ? "" : getEffective().getApiKey();
        } else {
            apiKey = dto.getApiKey().trim();
        }

        AiConfig row = new AiConfig();
        row.setId(SINGLETON_ID);
        row.setBaseUrl(dto.getBaseUrl().trim());
        row.setApiKey(apiKey);
        row.setModel(dto.getModel().trim());
        row.setTemperature(dto.getTemperature() == null
                ? new java.math.BigDecimal("0.7") : dto.getTemperature());
        row.setTimeoutSeconds(dto.getTimeoutSeconds() == null ? 60 : dto.getTimeoutSeconds());
        row.setEmbeddingProvider(StringUtils.hasText(dto.getEmbeddingProvider())
                ? dto.getEmbeddingProvider() : "auto");
        row.setEmbeddingModel(StringUtils.hasText(dto.getEmbeddingModel())
                ? dto.getEmbeddingModel().trim() : "embedding-3");
        row.setLocalDim(dto.getLocalDim() == null ? 512 : dto.getLocalDim());
        row.setUpdatedBy(operator);
        row.setUpdatedAt(LocalDateTime.now());
        // 单行 upsert：存在则更新，不存在则插入
        if (aiConfigMapper.selectById(SINGLETON_ID) == null) {
            aiConfigMapper.insert(row);
        } else {
            aiConfigMapper.updateById(row);
        }
        log.info("AI 配置已更新：操作人={}，模型={}，向量化={}", operator, row.getModel(),
                row.getEmbeddingProvider());

        String after = embeddingSignature(getEffective());
        eventPublisher.publishEvent(new AiConfigChangedEvent(dto, !before.equals(after)));
    }

    @Override
    @Transactional
    public void reset(String operator) {
        AiConfig row = loadRow();
        if (row == null) {
            return;
        }
        aiConfigMapper.deleteById(row.getId());
        log.info("AI 配置已恢复默认（回落环境变量）：操作人={}", operator);
        eventPublisher.publishEvent(new AiConfigChangedEvent(null, true));
    }

    @Override
    public String embeddingSignature(AiProperties properties) {
        AiProperties.Embedding e = properties.getEmbedding();
        String provider = e.getProvider() == null ? "auto" : e.getProvider().toLowerCase();
        boolean remote = "remote".equals(provider)
                || ("auto".equals(provider) && properties.enabled());
        return remote
                ? "remote:" + e.getModel()
                : "local:" + (e.getLocalDim() == null ? 512 : e.getLocalDim());
    }

    private AiConfig loadRow() {
        return aiConfigMapper.selectOne(new LambdaQueryWrapper<AiConfig>()
                .eq(AiConfig::getId, SINGLETON_ID)
                .last("limit 1"));
    }

    /** 向量化实现的中文标签（供管理端展示，不依赖 Bean 解析） */
    private String implLabel(AiProperties p) {
        return embeddingSignature(p).startsWith("remote")
                ? "远程 Embedding（" + p.getEmbedding().getModel() + "）"
                : "本地特征哈希（" + p.getEmbedding().getLocalDim() + " 维）";
    }

    /** 密钥脱敏：仅保留前 6 后 4，短密钥全掩码 */
    static String mask(String key) {
        if (!StringUtils.hasText(key)) {
            return "";
        }
        int len = key.length();
        if (len <= 8) {
            return "****";
        }
        return key.substring(0, 6) + "****" + key.substring(len - 4);
    }
}
