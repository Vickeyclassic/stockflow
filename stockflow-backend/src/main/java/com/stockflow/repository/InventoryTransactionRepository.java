package com.stockflow.repository;
import com.stockflow.entity.*; import java.time.Instant; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction,Long>,JpaSpecificationExecutor<InventoryTransaction>{
 boolean existsByProductId(Long productId);
 @Query("select coalesce(sum(t.quantity),0) from InventoryTransaction t where t.createdAt >= :since and t.transactionType in :types")
 long totalSince(@Param("since") Instant since,@Param("types") java.util.List<TransactionType> types);
}