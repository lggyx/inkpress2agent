package com.inkpress.service;

import com.inkpress.entity.Category;

import java.util.List;

public interface CategoryService {

    List<Category> listAll();

    void save(Category category);

    void update(Category category);

    void delete(Long id);
}
