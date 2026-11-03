package com.orionkey.finance;

import com.orionkey.entity.*;
import com.orionkey.finance.entity.*;
import com.orionkey.finance.repository.*;
import com.orionkey.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.orionkey.finance.FinanceSupport.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CompanyPricingService {
    private final CompanyRepository companies;
    private final CompanyQuoteRepository quotes;
    private final UserRepository users;
    private final ProductRepository products;
    private final ProductSpecRepository specs;
    private final WholesaleRuleRepository wholesale;
    private final FinanceSupport support;
    public record CompanyInput(String name,boolean enabled,String note) {}
    public record PriceQuote(BigDecimal unitPrice,String source,UUID quoteId,UUID companyId,String companyName) {}
    public List<Company> companies(){return companies.findAll(Sort.by("name"));}
    public List<CompanyQuote> quotes(UUID companyId){return quotes.findByCompanyIdOrderByCreatedAtDesc(companyId);}
    public List<Map<String,Object>> members(UUID companyId){
        return users.findByCompanyId(companyId).stream().map(u->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",u.getId());m.put("username",u.getUsername());m.put("email",u.getEmail());m.put("company_id",u.getCompanyId());return m;}).toList();
    }
    @Transactional public Company saveCompany(UUID id,CompanyInput input,UUID actor){
        String name=text(input.name(),120,"企业名称");if(input.note()!=null && input.note().length()>500)throw bad("企业备注最多500字符");
        Company company=id==null?new Company():companies.lockById(id).orElseThrow(()->bad("企业不存在"));
        String before=id==null?null:support.json(company);company.setName(name);company.setEnabled(input.enabled());company.setNote(input.note());companies.save(company);
        support.audit("company.save",company.getId(),before,input,null,actor);return company;
    }
    @Transactional public void bindUser(UUID companyId,UUID userId,UUID actor){
        if(companyId!=null) companies.lockById(companyId).filter(Company::isEnabled).orElseThrow(()->bad("企业不存在或已停用"));
        User user=users.findById(userId).filter(u->u.getIsDeleted()==0).orElseThrow(()->bad("用户不存在"));
        UUID before=user.getCompanyId();user.setCompanyId(companyId);users.save(user);
        support.audit("company.bind_user",userId,before,companyId,null,actor);
    }
    @Transactional public CompanyQuote saveQuote(UUID companyId,UUID productId,UUID specId,BigDecimal price,UUID actor){
        companies.lockById(companyId).filter(Company::isEnabled).orElseThrow(()->bad("企业不存在或已停用"));
        Product product=products.findById(productId).filter(p->p.getIsDeleted()==0).orElseThrow(()->bad("商品不存在"));validateSpec(product,specId);
        price=amount(price,false);String key=sku(productId,specId);
        var old=quotes.findFirstByCompanyIdAndSkuKeyAndActiveTrue(companyId,key);long revision=old.map(q->q.getRevision()+1).orElse(1L);
        old.ifPresent(q->{q.setActive(false);quotes.saveAndFlush(q);});
        CompanyQuote quote=new CompanyQuote();quote.setCompanyId(companyId);quote.setProductId(productId);quote.setSpecId(specId);quote.setSkuKey(key);quote.setUnitPrice(price);quote.setRevision(revision);quote.setCreatedBy(actor);quotes.save(quote);
        support.audit("company.quote",quote.getId(),old.map(CompanyQuote::getId).orElse(null),quote,null,actor);return quote;
    }
    public PriceQuote resolve(UUID userId,Product product,UUID specId,int quantity){
        quantity(quantity);validateSpec(product,specId);
        Company company=companyFor(userId);
        if(company!=null){
            var quote=quotes.findFirstByCompanyIdAndSkuKeyAndActiveTrue(company.getId(),sku(product.getId(),specId));
            if(quote.isPresent())return new PriceQuote(quote.get().getUnitPrice(),"COMPANY",quote.get().getId(),company.getId(),company.getName());
        }
        BigDecimal price=specId==null?product.getBasePrice():specs.findById(specId).orElseThrow().getPrice();String source="RETAIL";
        if(product.isWholesaleEnabled()){
            var rules=specId==null?wholesale.findByProductIdAndSpecIdIsNullOrderByMinQuantityAsc(product.getId()):wholesale.findByProductIdAndSpecIdOrderByMinQuantityAsc(product.getId(),specId);
            for(int i=rules.size()-1;i>=0;i--)if(quantity>=rules.get(i).getMinQuantity()){price=rules.get(i).getUnitPrice();source="WHOLESALE";break;}
        }
        return new PriceQuote(price,source,null,company==null?null:company.getId(),company==null?null:company.getName());
    }
    public Company companyFor(UUID userId){
        if(userId==null)return null;
        User user=users.findById(userId).filter(u->u.getIsDeleted()==0).orElseThrow(()->bad("账户不存在或已停用"));
        if(user.getCompanyId()==null)return null;
        return companies.findById(user.getCompanyId()).filter(Company::isEnabled).orElseThrow(()->bad("企业账户已暂停，请联系商家"));
    }
    public void assertExpectedTotal(Object expected,BigDecimal actual){
        if(expected==null)return;
        try{if(amount(new BigDecimal(expected.toString()),false).compareTo(actual)!=0)throw bad("报价已变化，请刷新价格后重新确认下单");}
        catch(NumberFormatException e){throw bad("确认金额格式无效");}
    }
    private void validateSpec(Product product,UUID specId){
        if(product.isSpecEnabled() && specId==null)throw bad("请选择商品规格");
        if(specId!=null)specs.findById(specId).filter(s->s.getProductId().equals(product.getId()) && s.getIsDeleted()==0).orElseThrow(()->bad("商品规格不存在或不匹配"));
    }
    private String sku(UUID product,UUID spec){return product+":"+(spec==null?"-":spec);}
}
