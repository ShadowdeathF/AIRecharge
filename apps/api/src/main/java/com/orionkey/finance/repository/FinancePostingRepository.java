package com.orionkey.finance.repository;
import com.orionkey.finance.entity.FinancePosting;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface FinancePostingRepository extends JpaRepository<FinancePosting,UUID> {
 List<FinancePosting> findBySettlementIdOrderByCreatedAtAsc(UUID id);
List<FinancePosting> findByOriginId(UUID id);
@Query("select coalesce(sum(p.amount),0) from FinancePosting p where p.account=:account") java.math.BigDecimal total(@Param("account")String account);
@Query("select coalesce(sum(p.amount),0) from FinancePosting p where p.account='PERSONAL_PAYABLE' and p.personId=:person") java.math.BigDecimal personal(@Param("person")UUID person);
}
