package com.stockflow.repository;
import com.stockflow.entity.SalesOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItem,Long> {
 boolean existsByProductId(Long id);
}
