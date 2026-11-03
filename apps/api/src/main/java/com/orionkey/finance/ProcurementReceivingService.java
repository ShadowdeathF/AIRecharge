package com.orionkey.finance;
import com.orionkey.finance.entity.PurchaseLot;
import com.orionkey.repository.CardKeyRepository;
import com.orionkey.service.AdminCardKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.orionkey.finance.FinanceSupport.*;

@Service @RequiredArgsConstructor
public class ProcurementReceivingService {
    private final ProcurementService purchases;
    private final AdminCardKeyService cards;
    private final CardKeyRepository keys;
    public record Input(ProcurementService.ReceiveInput receipt,String cardKeys){}
    @Transactional public PurchaseLot receive(UUID purchaseId,Input input,UUID actor){
        if(input.receipt()==null || input.cardKeys()==null || input.cardKeys().isBlank())throw bad("请填写采购入库信息与卡密");
        var content=Arrays.stream(input.cardKeys().split("\\R")).map(String::trim).filter(s->!s.isEmpty()).toList();
        if(content.size()!=input.receipt().quantity() || new HashSet<>(content).size()!=content.size())throw bad("卡密不得重复，数量须与本次采购入库一致");
        PurchaseLot lot=purchases.receive(purchaseId,input.receipt(),actor);
        if(lot.getCardImportBatchId()!=null){
            var existing=keys.findByImportBatchId(lot.getCardImportBatchId()).stream().map(com.orionkey.entity.CardKey::getContent).collect(java.util.stream.Collectors.toSet());
            if(!existing.equals(new HashSet<>(content)))throw bad("此入库请求已关联其他卡密，请核对原批次");return lot;
        }
        Map<String,Object> request=new HashMap<>();request.put("product_id",lot.getProductId().toString());request.put("spec_id",lot.getSpecId()==null?null:lot.getSpecId().toString());request.put("content",input.cardKeys());request.put("procurement_lot_id",lot.getId().toString());
        cards.importCardKeys(request,actor);
        return purchases.detail(purchaseId).lots().stream().filter(l->l.getId().equals(lot.getId())).findFirst().orElseThrow();
    }
}
