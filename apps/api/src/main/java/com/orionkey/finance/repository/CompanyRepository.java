package com.orionkey.finance.repository;
import com.orionkey.finance.entity.Company;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface CompanyRepository extends JpaRepository<Company,UUID> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from Company c where c.id=:id") Optional<Company> lockById(@Param("id")UUID id);
}
