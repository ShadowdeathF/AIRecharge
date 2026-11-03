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
@Table(name="finance_purchase_orders")
public class PurchaseOrder extends BaseEntity {
    @Column(nullable=false,length=120) private String supplier;
    private LocalDate purchasedOn;
    @Column(nullable=false,length=20) private String kind;
    @Column(nullable=false,length=20) private String status = "OPEN";
    @Column(nullable=false,unique=true,length=128) private String idempotencyKey;
    @Column(length=500) private String note;
    private UUID createdBy;
    @Version private long version;
}

