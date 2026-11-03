package com.orionkey.finance.entity;
import com.orionkey.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
@Getter @Setter @Entity @Table(name="finance_postings")
public class FinancePosting extends BaseEntity {
 @Column(nullable=false) private UUID settlementId;
 @Column(nullable=false) private UUID originId;
 private UUID personId;
 @Column(nullable=false,length=30) private String account;
 @Column(precision=18,scale=2,nullable=false) private BigDecimal amount;
 private LocalDate accountingDate;
 @Column(nullable=false,unique=true,length=160) private String postingKey;
}
