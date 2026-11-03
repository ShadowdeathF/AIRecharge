package com.orionkey.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.constant.ErrorCode;
import com.orionkey.exception.BusinessException;
import com.orionkey.finance.entity.FinanceAudit;
import com.orionkey.finance.repository.FinanceAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FinanceSupport {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final ObjectMapper mapper;
    private final FinanceAuditRepository audits;
    private final com.orionkey.finance.repository.FinanceControlRepository controls;
    private volatile com.fasterxml.jackson.databind.ObjectWriter financeWriter;
    public void guard() { controls.lockMain().orElseThrow(()->bad("财务初始化未完成")); }
    public static void version(long actual,Long expected) { if(expected==null || expected!=actual) throw bad("记录已变更，请刷新后再操作"); }
    public static BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST,message); }
    public static String text(String value,int max,String label) {
        if(value==null || value.isBlank() || value.length()>max) throw bad(label+"不能为空且不得超过"+max+"字符");
        return value.trim();
    }
    public static BigDecimal amount(BigDecimal value,boolean signed) {
        if(value==null || value.scale()>2 || (!signed && value.signum()<0) || value.abs().compareTo(new BigDecimal("999999999999.99"))>0)
            throw bad("金额须为有效的两位小数，且在允许范围内");
        return value.setScale(2);
    }
    public static int quantity(int value) { if(value<1 || value>999999) throw bad("数量须为1到999999的整数"); return value; }
    public static LocalDate date(LocalDate value) { if(value==null || value.getYear()<2000 || value.isAfter(LocalDate.now(ZONE))) throw bad("业务日期无效或晚于今天"); return value; }
    public String json(Object value) {
        try{
            if(financeWriter==null)synchronized(this){
                if(financeWriter==null){
                    var decimalModule=new com.fasterxml.jackson.databind.module.SimpleModule();
                    decimalModule.addSerializer(BigDecimal.class,com.fasterxml.jackson.databind.ser.std.ToStringSerializer.instance);
                    financeWriter=mapper.copy().registerModule(decimalModule).writer();
                }
            }
            return financeWriter.writeValueAsString(value);
        }catch(Exception e){throw new IllegalStateException("财务快照序列化失败",e);}
    }
    public void audit(String action,UUID target,Object before,Object after,String reason,UUID actor) {
        FinanceAudit row=new FinanceAudit(); row.setAction(action);row.setTargetId(target);row.setActorId(actor);
        row.setBeforeData(before==null?null:json(before));row.setAfterData(after==null?null:json(after));row.setReason(reason);
        audits.save(row);
    }
}
