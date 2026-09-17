package com.stockflow.repository;
import com.stockflow.entity.*; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.domain.*;
public interface StockDocumentRepository extends JpaRepository<StockDocument,Long>{
 boolean existsByKindAndNumber(ReferenceType kind,String number);
 boolean existsBySupplierId(Long supplierId);
 Page<StockDocument> findByKind(ReferenceType kind,Pageable pageable);
}