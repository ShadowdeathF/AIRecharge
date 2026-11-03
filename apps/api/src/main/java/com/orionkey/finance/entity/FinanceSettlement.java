package com.orionkey.finance.entity;
import com.orionkey.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
@Getter @Setter @Entity @Table(name="finance_settlements")
public class FinanceSettlement extends BaseEntity {
 @Column(nullable=false,length=30) private String kind;
 private UUID originId;
 @Column(unique=true,length=12) private String quarterKey;
 private LocalDate accountingDate;
 @Column(precision=18,scale=2,nullable=false) private BigDecimal totalAmount=BigDecimal.ZERO;
 @Column(precision=18,scale=2,nullable=false) private BigDecimal operatingBase=BigDecimal.ZERO;
 @Column(precision=18,scale=2,nullable=false) private BigDecimal roundingAdjustment=BigDecimal.ZERO;
 @Column(precision=18,scale=2,nullable=false) private BigDecimal operatingAmount=BigDecimal.ZERO;
 @Column(precision=18,scale=2,nullable=false) private BigDecimal dividendAmount=BigDecimal.ZERO;
 @Column(columnDefinition="TEXT",nullable=false) private String snapshotJson;
 @Column(nullable=false,unique=true,length=128) private String idempotencyKey;
 private UUID createdBy;
}
