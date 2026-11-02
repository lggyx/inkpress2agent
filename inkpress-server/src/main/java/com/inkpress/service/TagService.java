package com.inkpress.service;

import com.inkpress.entity.Tag;

import java.util.List;

public interface TagService {

    List<Tag> listAll();

    void save(Tag tag);

    void update(Tag tag);

    void delete(Long id);
}
