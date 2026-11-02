package com.inkpress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 保存文章（新增/编辑共用）
 */
@Data
public class ArticleSaveDTO {

    /** 编辑时传入，新增为空 */
    private Long id;

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最长 200 字")
    private String title;

    @Size(max = 500, message = "摘要最长 500 字")
    private String summary;

    @NotBlank(message = "正文不能为空")
    @Size(max = 100000, message = "正文最长 100000 字")
    private String content;

    private String cover;

    private Long categoryId;

    /** 标签名称列表，不存在的标签自动创建，最多 10 个、单个最长 50 字（与 tag.name 字段一致） */
    @Size(max = 10, message = "标签最多 10 个")
    private List<@Size(max = 50, message = "单个标签最长 50 字") String> tags;

    @NotBlank(message = "状态不能为空")
    private String status;

    private Boolean isTop = false;
}
