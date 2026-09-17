package com.stockflow.repository;
import com.stockflow.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder,Long>,JpaSpecificationExecutor<PurchaseOrder> {
 boolean existsBySupplierId(Long id);
 boolean existsByOrderNumberAndIdNot(String number,Long id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from PurchaseOrder o where o.id=:id")
 Optional<PurchaseOrder> findForUpdate(Long id);
}

