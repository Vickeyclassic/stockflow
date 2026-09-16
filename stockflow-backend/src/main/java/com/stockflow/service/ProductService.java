package com.stockflow.service;

import com.stockflow.dto.*;
import com.stockflow.entity.*;
import com.stockflow.exception.DomainException;
import com.stockflow.repository.*;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final SupplierRepository suppliers;
    public ProductService(ProductRepository products, CategoryRepository categories, SupplierRepository suppliers) {
        this.products = products; this.categories = categories; this.suppliers = suppliers;
    }
    public ProductResponse get(Long id) { return DtoMapper.product(products.findById(id).orElseThrow(() -> DomainException.notFound("Product", id))); }
    public PageResponse<ProductResponse> list(String name, String sku, Long categoryId, Long supplierId, Boolean active, Boolean lowStock, int page, int size) {
        return PageResponse.from(products.findAll(ProductSpecifications.filter(name, sku, categoryId, supplierId, active, lowStock),
                PageRequest.of(page, size, Sort.by("id").descending())).map(DtoMapper::product));
    }
    @Transactional
    public ProductResponse create(ProductCreateRequest r) {
        Product p = new Product();
        apply(p, r.sku(), r.name(), r.description(), r.categoryId(), r.supplierId(), r.costPrice(), r.sellingPrice(), r.reorderLevel(), r.unit(), r.active() == null || r.active());
        p.setQuantityInStock(r.quantityInStock() == null ? 0 : r.quantityInStock());
        return DtoMapper.product(products.saveAndFlush(p));
    }
    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest r) {
        Product p = locked(id);
        apply(p, r.sku(), r.name(), r.description(), r.categoryId(), r.supplierId(), r.costPrice(), r.sellingPrice(), r.reorderLevel(), r.unit(), r.active());
        // Stock is intentionally absent from the update DTO and untouched here.
        return DtoMapper.product(products.saveAndFlush(p));
    }
    private void apply(Product p, String sku, String name, String description, Long categoryId, Long supplierId, BigDecimal cost, BigDecimal selling, Integer reorder, String unit, Boolean active) {
        String normalizedSku = sku.toUpperCase(Locale.ROOT);
        boolean duplicate = p.getId() == null ? products.existsBySku(normalizedSku) : products.existsBySkuAndIdNot(normalizedSku, p.getId());
        if (duplicate) throw DomainException.conflict("DUPLICATE_SKU", "A product with this SKU already exists");
        p.setSku(normalizedSku); p.setName(name.strip()); p.setDescription(DtoMapper.clean(description));
        p.setCategory(categories.findById(categoryId).orElseThrow(() -> DomainException.notFound("Category", categoryId)));
        p.setSupplier(supplierId == null ? null : suppliers.findById(supplierId).orElseThrow(() -> DomainException.notFound("Supplier", supplierId)));
        p.setCostPrice(cost); p.setSellingPrice(selling); p.setReorderLevel(reorder); p.setUnit(unit.strip()); p.setActive(active);
    }
    @Transactional
    public ProductResponse adjustStock(Long id, StockAdjustmentRequest request) {
        Product p = locked(id);
        if (request.adjustment() == 0) throw DomainException.invalidStock("Adjustment must not be zero");
        long result = (long) p.getQuantityInStock() + request.adjustment();
        if (result < 0) throw DomainException.invalidStock("Adjustment would make stock negative");
        if (result > Integer.MAX_VALUE) throw DomainException.invalidStock("Adjustment exceeds the supported stock quantity");
        p.setQuantityInStock((int) result);
        // A future inventory transaction record belongs in this same transaction.
        return DtoMapper.product(products.saveAndFlush(p));
    }
    @Transactional
    public void delete(Long id) { products.delete(locked(id)); products.flush(); }
    private Product locked(Long id) { return products.findForUpdate(id).orElseThrow(() -> DomainException.notFound("Product", id)); }
}

