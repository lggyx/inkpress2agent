package com.inkpress.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 服务配置保存入参（管理端）。
 * apiKey 语义：null=保持当前生效密钥不变（前端未输入时传 null）；空串=清除密钥；其他值=替换。
 * 其余字段为完整替换语义，不支持部分字段留空。
 */
@Data
public class AiConfigDTO {

    @NotBlank(message = "base-url 不能为空")
    @Size(max = 255, message = "base-url 最长 255 字符")
    @Pattern(regexp = "^https?://\\S+$", message = "base-url 须为 http(s) 地址")
    private String baseUrl;

    @Size(max = 255, message = "api-key 最长 255 字符")
    private String apiKey;

    @NotBlank(message = "模型名不能为空")
    @Size(max = 100, message = "模型名最长 100 字符")
    private String model;

    @DecimalMin(value = "0.0", message = "温度不能小于 0")
    @DecimalMax(value = "2.0", message = "温度不能大于 2")
    private java.math.BigDecimal temperature;

    @Min(value = 5, message = "超时时间不能小于 5 秒")
    @Max(value = 300, message = "超时时间不能大于 300 秒")
    private Integer timeoutSeconds;

    @Pattern(regexp = "^(auto|local|remote)$", message = "向量化方式须为 auto/local/remote")
    private String embeddingProvider;

    @Size(max = 100, message = "向量模型名最长 100 字符")
    private String embeddingModel;

    @Min(value = 64, message = "本地向量维度不能小于 64")
    @Max(value = 4096, message = "本地向量维度不能大于 4096")
    private Integer localDim;
}
