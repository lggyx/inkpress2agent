package com.inkpress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 摘要 / 标签推荐请求
 */
@Data
public class AiContentDTO {

    @NotBlank(message = "内容不能为空")
    @Size(max = 30000, message = "内容过长")
    private String content;
}
