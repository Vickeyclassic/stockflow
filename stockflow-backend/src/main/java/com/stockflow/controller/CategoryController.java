package com.stockflow.controller;

import com.stockflow.dto.*;
import com.stockflow.service.CategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService service;
    public CategoryController(CategoryService service) { this.service = service; }
    @GetMapping public List<CategoryResponse> list() { return service.list(); }
    @GetMapping("/{id}") public CategoryResponse get(@PathVariable @Positive Long id) { return service.get(id); }
    @PostMapping public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        var result = service.create(request);
        return ResponseEntity.created(URI.create("/api/categories/" + result.id())).body(result);
    }
    @PutMapping("/{id}") public CategoryResponse update(@PathVariable @Positive Long id, @Valid @RequestBody CategoryRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable @Positive Long id) { service.delete(id); return ResponseEntity.noContent().build(); }
}

