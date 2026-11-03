package com.orionkey.finance.entity;
import com.orionkey.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
import java.math.BigDecimal;
@Getter @Setter @Entity @Table(name="finance_company_quotes")
public class CompanyQuote extends BaseEntity {
 @Column(nullable=false) private UUID companyId;
 @Column(nullable=false) private UUID productId;
 private UUID specId;
 @Column(nullable=false,length=80) private String skuKey;
 @Column(precision=18,scale=2,nullable=false) private BigDecimal unitPrice;
 private long revision;
 private boolean active=true;
 private UUID createdBy;
}
