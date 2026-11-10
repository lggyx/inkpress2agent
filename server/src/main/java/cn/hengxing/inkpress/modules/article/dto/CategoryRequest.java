package cn.hengxing.inkpress.modules.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(Long id, Long parentId, @NotBlank @Size(max = 64) String name, Integer sort) {
}
