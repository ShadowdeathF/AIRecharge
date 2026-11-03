package com.orionkey.finance;
import org.springframework.stereotype.Component;
import java.time.*;
@Component
public class FinanceTime {
    public LocalDate today(){return LocalDate.now(FinanceSupport.ZONE);}
    public LocalDateTime now(){return LocalDateTime.now(FinanceSupport.ZONE);}
}
