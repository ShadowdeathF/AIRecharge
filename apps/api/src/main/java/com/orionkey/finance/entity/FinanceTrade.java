package com.orionkey.finance.entity;
import com.orionkey.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Getter @Setter @Entity @Table(name="finance_trades")
public class FinanceTrade extends BaseEntity {
 @Column(unique=true) private UUID sourceOrderId;
 @Column(nullable=false,unique=true,length=255) private String sourceKey;
 @Column(nullable=false,unique=true,length=128) private String idempotencyKey;
 @Column(length=64) private String creationFingerprint;
 private UUID companyId;
 private String companyName;
 private String customerName;
 @Column(nullable=false,length=20) private String source;
 @Column(length=120) private String externalReference;
 private LocalDate businessDate;
 @Column(precision=18,scale=2,nullable=false) private BigDecimal dueAmount=BigDecimal.ZERO;
 @Column(precision=18,scale=2,nullable=false) private BigDecimal receivedAmount=BigDecimal.ZERO;
 private boolean receiptFinal;
 @Column(precision=18,scale=2) private BigDecimal costAmount;
 @Column(columnDefinition="TEXT",nullable=false) private String itemsJson;
 private UUID referrerId;
 @Column(length=500) private String note;
 @Column(nullable=false,length=20) private String status="DRAFT";
 private LocalDateTime confirmedAt;
 private UUID settlementId;
 private UUID createdBy;
 @Version private long version;
}
