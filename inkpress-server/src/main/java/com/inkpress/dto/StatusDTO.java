package com.inkpress.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 状态修改请求（文章发布/下架、评论审核共用）
 */
@Data
public class StatusDTO {

    @NotBlank(message = "状态不能为空")
    private String status;
}
