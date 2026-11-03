package com.orionkey.finance.repository;
import com.orionkey.finance.entity.FinanceTrade;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface FinanceTradeRepository extends JpaRepository<FinanceTrade,UUID> {
 Optional<FinanceTrade> findBySourceOrderId(UUID orderId);
Optional<FinanceTrade> findBySourceKey(String key);
Optional<FinanceTrade> findByIdempotencyKey(String key);
@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from FinanceTrade t where t.id=:id") Optional<FinanceTrade> lockById(@Param("id")UUID id);
List<FinanceTrade> findByStatusOrderByConfirmedAtAsc(String status);
List<FinanceTrade> findByBusinessDateBetweenOrderByBusinessDateDescCreatedAtDesc(java.time.LocalDate start,java.time.LocalDate end);
@Query("select o from Order o where o.paidAt is not null and o.status in (com.orionkey.constant.OrderStatus.PAID,com.orionkey.constant.OrderStatus.DELIVERED) and not exists(select t.id from FinanceTrade t where t.sourceOrderId=o.id) order by o.paidAt,o.id") List<com.orionkey.entity.Order> unsynced(org.springframework.data.domain.Pageable page);
}
