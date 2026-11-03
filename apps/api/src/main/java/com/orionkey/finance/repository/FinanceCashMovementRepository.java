package com.orionkey.finance.repository;
import com.orionkey.finance.entity.FinanceCashMovement;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface FinanceCashMovementRepository extends JpaRepository<FinanceCashMovement,UUID> {
 Optional<FinanceCashMovement> findByIdempotencyKey(String key);
List<FinanceCashMovement> findByTradeIdOrderByCreatedAtAsc(UUID tradeId);
List<FinanceCashMovement> findByPersonIdOrderByCreatedAtAsc(UUID personId);
List<FinanceCashMovement> findByKindOrderByHappenedOnDesc(String kind);
}
