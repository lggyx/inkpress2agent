package cn.hengxing.inkpress.modules.search.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public record SearchHit(Long id, String title, String highlight) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("highlight", highlight);
        return m;
    }
}
