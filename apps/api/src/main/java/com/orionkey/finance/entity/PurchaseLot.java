package com.orionkey.finance.entity;

import com.orionkey.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name="finance_purchase_lots")
public class PurchaseLot extends BaseEntity {
    @Column(nullable=false) private UUID purchaseId;
    @Column(nullable=false) private UUID purchaseLineId;
    @Column(nullable=false) private UUID productId;
    private UUID specId;
    private int quantity;
    @Column(precision=18,scale=2,nullable=false) private BigDecimal totalCost;
    private int issuedQuantity;
    @Column(precision=18,scale=2,nullable=false) private BigDecimal issuedCost = BigDecimal.ZERO;
    @Column(unique=true) private UUID cardImportBatchId;
    private LocalDate receivedOn;
    @Column(nullable=false,unique=true,length=128) private String idempotencyKey;
    private UUID createdBy;
    @Version private long version;
}

