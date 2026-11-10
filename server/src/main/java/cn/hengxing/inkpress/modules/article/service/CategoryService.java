package cn.hengxing.inkpress.modules.article.service;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.article.dto.CategoryRequest;
import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.entity.Category;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.article.mapper.CategoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    public CategoryService(CategoryMapper categoryMapper, ArticleMapper articleMapper) {
        this.categoryMapper = categoryMapper;
        this.articleMapper = articleMapper;
    }

    public List<Category> tree() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort).orderByAsc(Category::getId));
    }

    @Transactional
    public Long save(CategoryRequest req) {
        Category category;
        if (req.id() == null) {
            category = new Category();
            category.setParentId(req.parentId() == null ? 0L : req.parentId());
        } else {
            category = categoryMapper.selectById(req.id());
            if (category == null) {
                throw BizException.of(2101, "分类不存在");
            }
            // 防止把分类挂到自己的子孙节点下
            Long newParent = req.parentId() == null ? 0L : req.parentId();
            if (newParent.equals(category.getId())) {
                throw BizException.of(2102, "父分类不能是自己");
            }
            category.setParentId(newParent);
        }
        category.setName(req.name());
        category.setSort(req.sort() == null ? 0 : req.sort());
        if (req.id() == null) {
            categoryMapper.insert(category);
        } else {
            categoryMapper.updateById(category);
        }
        return category.getId();
    }

    @Transactional
    public void delete(Long id) {
        Long children = categoryMapper.selectCount(new LambdaQueryWrapper<Category>().eq(Category::getParentId, id));
        if (children > 0) {
            throw BizException.of(2103, "该分类下还有子分类");
        }
        Long articles = articleMapper.selectCount(new LambdaQueryWrapper<Article>().eq(Article::getCategoryId, id));
        if (articles > 0) {
            throw BizException.of(2104, "该分类下还有 " + articles + " 篇文章");
        }
        categoryMapper.deleteById(id);
    }
}
