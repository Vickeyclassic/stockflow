package com.stockflow.service;

import com.stockflow.dto.*;
import com.stockflow.entity.*;

final class DtoMapper {
    private DtoMapper() {}
    static CategoryResponse category(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getCreatedAt(), c.getUpdatedAt());
    }
    static SupplierResponse supplier(Supplier s) {
        return new SupplierResponse(s.getId(), s.getName(), s.getContactPerson(), s.getEmail(), s.getPhone(), s.getAddress(), s.getCreatedAt(), s.getUpdatedAt());
    }
    static ProductResponse product(Product p) {
        Supplier s = p.getSupplier();
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getCategory().getId(), p.getCategory().getName(),
                s == null ? null : s.getId(), s == null ? null : s.getName(), p.getCostPrice(), p.getSellingPrice(), p.getQuantityInStock(),
                p.getReorderLevel(), p.getUnit(), p.getActive(), p.isLowStock(), p.getCreatedAt(), p.getUpdatedAt());
    }
    static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}

