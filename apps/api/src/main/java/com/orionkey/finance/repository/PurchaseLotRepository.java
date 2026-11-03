package com.orionkey.finance.repository;

import com.orionkey.finance.entity.PurchaseLot;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface PurchaseLotRepository extends JpaRepository<PurchaseLot,UUID> {
    Optional<PurchaseLot> findByIdempotencyKey(String key);
    List<PurchaseLot> findByPurchaseIdOrderByCreatedAtAsc(UUID purchaseId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from PurchaseLot l where l.productId=:product and ((:spec is null and l.specId is null) or l.specId=:spec) order by l.receivedOn,l.id")
    List<PurchaseLot> lockSku(@Param("product") UUID product,@Param("spec") UUID spec);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from PurchaseLot l where l.id=:id")
    Optional<PurchaseLot> lockById(@Param("id") UUID id);
}
