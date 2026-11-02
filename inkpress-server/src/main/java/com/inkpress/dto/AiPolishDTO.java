package com.inkpress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 润色请求
 */
@Data
public class AiPolishDTO {

    @NotBlank(message = "内容不能为空")
    @Size(max = 20000, message = "润色内容最长 20000 字")
    private String content;

    /** 可选：用户的额外要求，如“更口语化一些” */
    @Size(max = 200, message = "润色要求最长 200 字")
    private String instruction;
}
