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
@Table(name="finance_purchase_lines")
public class PurchaseLine extends BaseEntity {
    @Column(nullable=false) private UUID purchaseId;
    @Column(nullable=false) private UUID productId;
    private UUID specId;
    @Column(nullable=false) private String productTitle;
    private String specName;
    private int quantity;
    private int receivedQuantity;
    @Version private long version;
}

