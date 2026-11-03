package com.orionkey.finance.repository;

import com.orionkey.finance.entity.FinanceAudit;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface FinanceAuditRepository extends JpaRepository<FinanceAudit,UUID> {
    List<FinanceAudit> findByTargetIdOrderByCreatedAtDesc(UUID targetId);
}

