package com.inkpress.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 服务配置（单行表，id 固定为 1）。
 * 管理端动态维护；无此行时回落环境变量配置（application.yml / AI_* 环境变量）。
 */
@Data
@TableName("ai_config")
public class AiConfig {

    /** 固定为 1，单行配置 */
    @TableId(type = IdType.INPUT)
    private Integer id;

    private String baseUrl;

    private String apiKey;

    private String model;

    private BigDecimal temperature;

    private Integer timeoutSeconds;

    /** auto：有 api-key 用远程 Embedding，否则本地；local：仅本地；remote：仅远程 */
    private String embeddingProvider;

    private String embeddingModel;

    private Integer localDim;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
