package com.stockflow.controller;

import com.stockflow.dto.*;
import com.stockflow.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService service;
    public ProductController(ProductService service) { this.service = service; }
    @GetMapping
    public PageResponse<ProductResponse> list(@RequestParam(required = false) @Size(max = 150) String name,
            @RequestParam(required = false) @Size(max = 64) String sku,
            @RequestParam(required = false) @Positive Long categoryId, @RequestParam(required = false) @Positive Long supplierId,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) Boolean lowStock,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(name, sku, categoryId, supplierId, active, lowStock, page, size);
    }
    @GetMapping("/{id}") public ProductResponse get(@PathVariable @Positive Long id) { return service.get(id); }
    @PostMapping public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
        var result = service.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + result.id())).body(result);
    }
    @PutMapping("/{id}") public ProductResponse update(@PathVariable @Positive Long id, @Valid @RequestBody ProductUpdateRequest request) { return service.update(id, request); }
    @PatchMapping("/{id}/stock") public ProductResponse stock(@PathVariable @Positive Long id, @Valid @RequestBody StockAdjustmentRequest request) { return service.adjustStock(id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable @Positive Long id) { service.delete(id); return ResponseEntity.noContent().build(); }
}

