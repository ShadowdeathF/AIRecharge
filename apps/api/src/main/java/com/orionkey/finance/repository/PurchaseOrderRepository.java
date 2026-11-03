package com.orionkey.finance.repository;

import com.orionkey.finance.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder,UUID> {
    Optional<PurchaseOrder> findByIdempotencyKey(String key);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseOrder p where p.id = :id")
    Optional<PurchaseOrder> lockById(@Param("id") UUID id);
}
