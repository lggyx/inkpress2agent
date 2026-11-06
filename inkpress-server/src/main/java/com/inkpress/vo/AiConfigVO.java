package com.inkpress.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 服务配置回显（管理端）。
 * 安全约定：apiKey 只返回脱敏值，完整密钥任何接口都不对前端暴露。
 */
@Data
public class AiConfigVO {

    /** 当前生效配置的来源：db=数据库配置，env=环境变量配置 */
    private String source;

    private String baseUrl;

    /** 脱敏后的密钥，如 zhipu-****9f3a；未配置时为空串 */
    private String apiKeyMasked;

    /** 是否已配置密钥（决定 AI 功能可用性） */
    private Boolean hasApiKey;

    private String model;

    private Double temperature;

    private Integer timeoutSeconds;

    private String embeddingProvider;

    private String embeddingModel;

    private Integer localDim;

    /** 当前生效的向量化实现名（如 hashing-bigram-512） */
    private String embeddingImpl;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
