package com.orionkey.finance.repository;
import com.orionkey.finance.entity.FinanceSettlement;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface FinanceSettlementRepository extends JpaRepository<FinanceSettlement,UUID> {
 Optional<FinanceSettlement> findByIdempotencyKey(String key);
Optional<FinanceSettlement> findByQuarterKey(String key);
List<FinanceSettlement> findByKindOrderByAccountingDateAsc(String kind);
List<FinanceSettlement> findByKindAndAccountingDateLessThanEqualOrderByAccountingDateAsc(String kind,java.time.LocalDate date);
List<FinanceSettlement> findByOriginIdOrderByCreatedAtAsc(UUID originId);
}
