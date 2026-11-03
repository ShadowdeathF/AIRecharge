package com.orionkey.finance.entity;
import com.orionkey.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Getter @Setter @Entity @Table(name="finance_people")
public class FinancePerson extends BaseEntity {
 @Column(nullable=false,length=80) private String name;
 @Column(length=20) private String baseRole;
 private boolean enabled=true;
 @Column(precision=18,scale=6,nullable=false) private BigDecimal dividendWeight=BigDecimal.ZERO;
 @Version private long version;
}
