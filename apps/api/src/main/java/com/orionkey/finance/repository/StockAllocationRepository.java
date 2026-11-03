package com.orionkey.finance.repository;

import com.orionkey.finance.entity.StockAllocation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface StockAllocationRepository extends JpaRepository<StockAllocation,UUID> {
    List<StockAllocation> findByTradeIdAndLineKeyOrderByCreatedAtAsc(UUID tradeId,String lineKey);
    List<StockAllocation> findByTradeId(UUID tradeId);
}
