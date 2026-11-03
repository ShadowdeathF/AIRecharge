package com.orionkey.finance.repository;
import com.orionkey.finance.entity.FinancePerson;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface FinancePersonRepository extends JpaRepository<FinancePerson,UUID> {
 List<FinancePerson> findByEnabledTrueOrderByCreatedAtAsc();
}
