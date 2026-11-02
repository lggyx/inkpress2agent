package com.inkpress.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inkpress.entity.Category;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface CategoryMapper extends BaseMapper<Category> {

    /** 各分类下的已发布文章数，用于看板饼图 */
    @Select("SELECT c.name AS name, COUNT(a.id) AS value FROM category c "
            + "LEFT JOIN article a ON a.category_id = c.id AND a.status = 'PUBLISHED' "
            + "GROUP BY c.id, c.name ORDER BY value DESC")
    List<Map<String, Object>> selectDistribution();
}
