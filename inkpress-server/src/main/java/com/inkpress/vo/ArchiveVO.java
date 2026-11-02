package com.inkpress.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 归档时间线（按年分组）
 */
@Data
@AllArgsConstructor
public class ArchiveVO {

    private String year;
    private List<Item> items;

    @Data
    @AllArgsConstructor
    public static class Item {
        private Long id;
        private String title;
        private LocalDateTime publishedAt;
    }
}
