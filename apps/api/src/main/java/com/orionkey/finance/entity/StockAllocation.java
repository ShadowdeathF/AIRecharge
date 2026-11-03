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
@Table(name="finance_stock_allocations", uniqueConstraints=@UniqueConstraint(columnNames={"trade_id","line_key","lot_id"}))
public class StockAllocation extends BaseEntity {
    @Column(nullable=false) private UUID tradeId;
    @Column(nullable=false,length=80) private String lineKey;
    @Column(nullable=false) private UUID lotId;
    private int quantity;
    @Column(precision=18,scale=2,nullable=false) private BigDecimal cost;
    private UUID createdBy;
}

