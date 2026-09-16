package com.stockflow.service;

import com.stockflow.dto.*;
import com.stockflow.entity.Category;
import com.stockflow.repository.*;
import com.stockflow.exception.DomainException;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categories;
    private final ProductRepository products;
    public CategoryService(CategoryRepository categories, ProductRepository products) { this.categories = categories; this.products = products; }
    public List<CategoryResponse> list() { return categories.findAll(Sort.by("name").and(Sort.by("id"))).stream().map(DtoMapper::category).toList(); }
    public CategoryResponse get(Long id) { return DtoMapper.category(require(id)); }
    private Category require(Long id) { return categories.findById(id).orElseThrow(() -> DomainException.notFound("Category", id)); }
    @Transactional
    public CategoryResponse create(CategoryRequest request) { return save(new Category(), request); }
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) { return save(require(id), request); }
    private CategoryResponse save(Category category, CategoryRequest request) {
        String name = request.name().strip();
        String normalized = name.toLowerCase(Locale.ROOT);
        boolean exists = category.getId() == null ? categories.existsByNormalizedName(normalized) : categories.existsByNormalizedNameAndIdNot(normalized, category.getId());
        if (exists) throw DomainException.conflict("DUPLICATE_CATEGORY", "A category with this name already exists");
        category.setName(name); category.setNormalizedName(normalized); category.setDescription(DtoMapper.clean(request.description()));
        return DtoMapper.category(categories.saveAndFlush(category));
    }
    @Transactional
    public void delete(Long id) {
        Category category = require(id);
        if (products.existsByCategoryId(id)) throw DomainException.conflict("RESOURCE_IN_USE", "Category is used by products and cannot be deleted");
        categories.delete(category); categories.flush();
    }
}

