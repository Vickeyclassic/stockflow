package com.stockflow.repository;
import com.stockflow.entity.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem,Long> {
 boolean existsByProductId(Long id);
}

