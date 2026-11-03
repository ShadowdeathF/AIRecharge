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
@Table(name="finance_audits")
public class FinanceAudit extends BaseEntity {
    @Column(nullable=false,length=80) private String action;
    private UUID targetId;
    private UUID actorId;
    @Column(columnDefinition="TEXT") private String beforeData;
    @Column(columnDefinition="TEXT") private String afterData;
    @Column(length=500) private String reason;
}

