package com.orionkey.finance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.*;

@Slf4j @Component @RequiredArgsConstructor
public class FinanceOrderSync {
    private final Worker worker;
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void committed(FinanceOrderEvent event){
        try{worker.sync(event);}catch(RuntimeException e){log.warn("Finance sync deferred for order {} ({})",event.orderId(),e.getClass().getSimpleName());}
    }
    @Service @RequiredArgsConstructor
    public static class Worker {
        private final FinanceTradeService trades;
        @Transactional(propagation=Propagation.REQUIRES_NEW)
        public void sync(FinanceOrderEvent event){trades.syncOrder(event.orderId());}
    }
}
