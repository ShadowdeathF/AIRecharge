package com.orionkey.finance.entity;
import com.orionkey.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Getter @Setter @Entity @Table(name="finance_cash_movements")
public class FinanceCashMovement extends BaseEntity {
 private UUID tradeId;
 private UUID personId;
 private UUID settlementId;
 @Column(nullable=false,length=30) private String kind;
 @Column(precision=18,scale=2,nullable=false) private BigDecimal amount;
 private LocalDate happenedOn;
 @Column(nullable=false,unique=true,length=128) private String idempotencyKey;
 @Column(length=128) private String reference;
 @Column(length=500) private String reason;
 @Column(unique=true) private UUID reversesId;
 private UUID createdBy;
}
