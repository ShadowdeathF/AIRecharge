package com.orionkey.finance.entity;
import com.orionkey.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
@Getter @Setter @Entity @Table(name="finance_adjustments")
public class FinanceAdjustment extends BaseEntity {
 @Column(nullable=false) private UUID tradeId;
 @Column(nullable=false) private UUID originId;
 private long sequenceNumber;
 @Column(precision=18,scale=2,nullable=false) private BigDecimal incomeDelta=BigDecimal.ZERO;
 @Column(precision=18,scale=2,nullable=false) private BigDecimal costDelta=BigDecimal.ZERO;
 private boolean changeReferrer;
 private UUID referrerId;
 @Column(nullable=false,length=500) private String reason;
 @Column(nullable=false,unique=true,length=128) private String idempotencyKey;
 private UUID createdBy;
}
