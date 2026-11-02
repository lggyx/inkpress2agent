package com.inkpress.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inkpress.common.BizException;
import com.inkpress.common.ErrorCode;
import com.inkpress.entity.ArticleTag;
import com.inkpress.entity.Tag;
import com.inkpress.mapper.ArticleTagMapper;
import com.inkpress.mapper.TagMapper;
import com.inkpress.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;

    @Override
    public List<Tag> listAll() {
        return tagMapper.selectList(new LambdaQueryWrapper<Tag>().orderByAsc(Tag::getId));
    }

    @Override
    public void save(Tag tag) {
        checkNameUnique(tag.getName(), null);
        tagMapper.insert(tag);
    }

    @Override
    public void update(Tag tag) {
        if (tag.getId() == null || tagMapper.selectById(tag.getId()) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        checkNameUnique(tag.getName(), tag.getId());
        tagMapper.updateById(tag);
    }

    @Override
    public void delete(Long id) {
        Long usedCount = articleTagMapper.selectCount(
                new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, id));
        if (usedCount != null && usedCount > 0) {
            throw BizException.of("该标签仍被文章使用，无法删除");
        }
        tagMapper.deleteById(id);
    }

    private void checkNameUnique(String name, Long excludeId) {
        Long count = tagMapper.selectCount(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getName, name)
                .ne(excludeId != null, Tag::getId, excludeId));
        if (count != null && count > 0) {
            throw BizException.of("标签名称已存在");
        }
    }
}
