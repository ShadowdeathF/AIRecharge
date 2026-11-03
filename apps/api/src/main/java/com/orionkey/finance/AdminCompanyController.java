package com.orionkey.finance;
import com.orionkey.common.ApiResponse;
import com.orionkey.context.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;
@RestController @RequestMapping("/admin/companies") @RequiredArgsConstructor
public class AdminCompanyController {
    private final CompanyPricingService service;
    public record QuoteInput(UUID productId,UUID specId,BigDecimal unitPrice){}
    @GetMapping public ApiResponse<?> list(){return ApiResponse.success(service.companies());}
    @PostMapping public ApiResponse<?> create(@RequestBody CompanyPricingService.CompanyInput request){return ApiResponse.success(service.saveCompany(null,request,RequestContext.getUserId()));}
    @PutMapping("/{id}") public ApiResponse<?> save(@PathVariable UUID id,@RequestBody CompanyPricingService.CompanyInput request){return ApiResponse.success(service.saveCompany(id,request,RequestContext.getUserId()));}
    @GetMapping("/{id}/quotes") public ApiResponse<?> quotes(@PathVariable UUID id){return ApiResponse.success(service.quotes(id));}
    @PostMapping("/{id}/quotes") public ApiResponse<?> quote(@PathVariable UUID id,@RequestBody QuoteInput input){return ApiResponse.success(service.saveQuote(id,input.productId(),input.specId(),input.unitPrice(),RequestContext.getUserId()));}
    @GetMapping("/{id}/members") public ApiResponse<?> members(@PathVariable UUID id){return ApiResponse.success(service.members(id));}
    @PutMapping("/{id}/members/{userId}") public ApiResponse<?> bind(@PathVariable UUID id,@PathVariable UUID userId){service.bindUser(id,userId,RequestContext.getUserId());return ApiResponse.success();}
    @DeleteMapping("/members/{userId}") public ApiResponse<?> unbind(@PathVariable UUID userId){service.bindUser(null,userId,RequestContext.getUserId());return ApiResponse.success();}
}
