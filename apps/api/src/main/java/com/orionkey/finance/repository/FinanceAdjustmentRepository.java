package com.orionkey.finance.repository;
import com.orionkey.finance.entity.FinanceAdjustment;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface FinanceAdjustmentRepository extends JpaRepository<FinanceAdjustment,UUID> {
 Optional<FinanceAdjustment> findByIdempotencyKey(String key);
List<FinanceAdjustment> findByOriginIdOrderBySequenceNumberAsc(UUID id);
List<FinanceAdjustment> findByTradeIdOrderBySequenceNumberAsc(UUID id);
 List<FinanceAdjustment> findByTradeIdIn(Collection<UUID> ids);
}
