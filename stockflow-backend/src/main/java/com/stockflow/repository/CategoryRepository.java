package com.stockflow.repository;
import com.stockflow.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByNormalizedName(String name);
    boolean existsByNormalizedNameAndIdNot(String name, Long id);
}

