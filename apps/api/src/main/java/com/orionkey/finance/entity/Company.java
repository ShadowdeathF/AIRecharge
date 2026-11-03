package com.orionkey.finance.entity;
import com.orionkey.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
import java.math.BigDecimal;
@Getter @Setter @Entity @Table(name="finance_companies")
public class Company extends BaseEntity {
 @Column(nullable=false,unique=true,length=120) private String name;
 private boolean enabled=true;
 @Column(length=500) private String note;
 @Version private long version;
}

