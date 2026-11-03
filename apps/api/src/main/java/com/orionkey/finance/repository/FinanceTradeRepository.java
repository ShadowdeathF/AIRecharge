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
Optional<FinanceTrade> findByCompanyIdAndExternalReferenceIgnoreCase(UUID companyId,String externalReference);
@Query("select distinct t from FinanceTrade t,CardKey k,CardImportBatch b where t.status='DRAFT' and t.costAmount is null and k.orderId=t.sourceOrderId and k.importBatchId=b.id and b.procurementLotId is not null") List<FinanceTrade> repairable(org.springframework.data.domain.Pageable page);
@Query("select o from Order o where o.companyId=:company and o.paidAt is not null and o.status in (com.orionkey.constant.OrderStatus.PAID,com.orionkey.constant.OrderStatus.DELIVERED) order by o.paidAt desc") List<com.orionkey.entity.Order> onlineOptions(@Param("company") UUID company,org.springframework.data.domain.Pageable page);
@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from FinanceTrade t where t.id=:id") Optional<FinanceTrade> lockById(@Param("id")UUID id);
List<FinanceTrade> findByStatusOrderByConfirmedAtAsc(String status);
List<FinanceTrade> findByBusinessDateBetweenOrderByBusinessDateDescCreatedAtDesc(java.time.LocalDate start,java.time.LocalDate end);
@Query("select o from Order o where o.paidAt is not null and o.status in (com.orionkey.constant.OrderStatus.PAID,com.orionkey.constant.OrderStatus.DELIVERED) and not exists(select t.id from FinanceTrade t where t.sourceOrderId=o.id) order by o.paidAt,o.id") List<com.orionkey.entity.Order> unsynced(org.springframework.data.domain.Pageable page);
}
