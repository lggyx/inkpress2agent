package com.inkpress.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inkpress.common.BizException;
import com.inkpress.common.ErrorCode;
import com.inkpress.entity.Article;
import com.inkpress.entity.Category;
import com.inkpress.mapper.ArticleMapper;
import com.inkpress.mapper.CategoryMapper;
import com.inkpress.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    @Override
    public List<Category> listAll() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
    }

    @Override
    public void save(Category category) {
        // 新增操作不允许携带 id，避免与自增主键冲突
        category.setId(null);
        checkNameUnique(category.getName(), null);
        categoryMapper.insert(category);
    }

    @Override
    public void update(Category category) {
        if (category.getId() == null || categoryMapper.selectById(category.getId()) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        checkNameUnique(category.getName(), category.getId());
        categoryMapper.updateById(category);
    }

    @Override
    public void delete(Long id) {
        Long articleCount = articleMapper.selectCount(
                new LambdaQueryWrapper<Article>().eq(Article::getCategoryId, id));
        if (articleCount != null && articleCount > 0) {
            throw BizException.of("该分类下仍有文章，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    private void checkNameUnique(String name, Long excludeId) {
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getName, name)
                .ne(excludeId != null, Category::getId, excludeId));
        if (count != null && count > 0) {
            throw BizException.of("分类名称已存在");
        }
    }
}
