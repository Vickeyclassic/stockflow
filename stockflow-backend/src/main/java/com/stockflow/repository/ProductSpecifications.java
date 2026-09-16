package com.stockflow.repository;

import com.stockflow.entity.Product;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {
    private ProductSpecifications() {}
    public static Specification<Product> filter(String name, String sku, Long categoryId, Long supplierId, Boolean active, Boolean lowStock) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (name != null && !name.isBlank()) predicates.add(cb.like(cb.lower(root.get("name")), contains(name), '!'));
            if (sku != null && !sku.isBlank()) predicates.add(cb.like(cb.lower(root.get("sku")), contains(sku), '!'));
            if (categoryId != null) predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            if (supplierId != null) predicates.add(cb.equal(root.get("supplier").get("id"), supplierId));
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            if (lowStock != null) {
                Predicate low = cb.lessThanOrEqualTo(root.get("quantityInStock"), root.get("reorderLevel"));
                predicates.add(lowStock ? low : cb.not(low));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
    private static String contains(String value) {
        return "%" + value.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}


