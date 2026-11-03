package com.orionkey.finance;
import com.orionkey.finance.entity.*;
import com.orionkey.finance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
@Component @RequiredArgsConstructor
public class FinanceBootstrap implements ApplicationRunner {
    private final FinanceControlRepository control;
    private final FinancePersonRepository people;
    @Override @Transactional public void run(ApplicationArguments args) {
        if(!control.existsById("MAIN"))control.saveAndFlush(new FinanceControl());
        control.lockMain().orElseThrow();
        if(people.count()==0){
            for(String[] role:new String[][]{{"TECH","技术总监"},{"BOSS","总经理"},{"FINANCE","财务总监"}}){
                FinancePerson person=new FinancePerson();person.setName(role[1]);person.setBaseRole(role[0]);person.setDividendWeight(BigDecimal.ONE);people.save(person);
            }
        }
    }
}
