package com.stockflow.repository;
import com.stockflow.entity.SalesOrder;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface SalesOrderRepository extends JpaRepository<SalesOrder,Long>,JpaSpecificationExecutor<SalesOrder> {
 boolean existsByCustomerId(Long id);
 boolean existsByOrderNumberAndIdNot(String number,Long id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from SalesOrder o where o.id=:id")
 Optional<SalesOrder> findForUpdate(Long id);
}
