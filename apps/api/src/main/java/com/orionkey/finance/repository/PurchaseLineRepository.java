package com.orionkey.finance.repository;

import com.orionkey.finance.entity.PurchaseLine;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface PurchaseLineRepository extends JpaRepository<PurchaseLine,UUID> {
    List<PurchaseLine> findByPurchaseIdOrderByCreatedAtAsc(UUID purchaseId);
}

