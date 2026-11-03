package com.orionkey.finance.repository;
import com.orionkey.finance.entity.CompanyQuote;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface CompanyQuoteRepository extends JpaRepository<CompanyQuote,UUID> {
 List<CompanyQuote> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
Optional<CompanyQuote> findFirstByCompanyIdAndSkuKeyAndActiveTrue(UUID companyId,String skuKey);
}
