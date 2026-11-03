package com.orionkey.finance.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @Entity @Table(name="finance_control")
public class FinanceControl {
    @Id @Column(length=20) private String id="MAIN";
    @Version private long version;
}
