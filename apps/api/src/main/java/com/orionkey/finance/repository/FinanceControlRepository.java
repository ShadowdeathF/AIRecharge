package com.orionkey.finance.repository;
import com.orionkey.finance.entity.FinanceControl;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface FinanceControlRepository extends JpaRepository<FinanceControl,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from FinanceControl c where c.id='MAIN'")
    Optional<FinanceControl> lockMain();
}
