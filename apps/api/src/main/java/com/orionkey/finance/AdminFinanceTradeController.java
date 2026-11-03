package com.orionkey.finance;
import com.orionkey.common.ApiResponse;
import com.orionkey.context.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping("/admin/finance/trades") @RequiredArgsConstructor
public class AdminFinanceTradeController {
    private final FinanceTradeService service;
    public record VersionInput(Long version){}
    @GetMapping public ApiResponse<?> list(@RequestParam("start_date") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate start,
        @RequestParam("end_date") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate end,@RequestParam(value="company_id",required=false)UUID company,
        @RequestParam(required=false)String source,@RequestParam(required=false)String status,@RequestParam(required=false)String keyword,
        @RequestParam(defaultValue="1")int page,@RequestParam(value="page_size",defaultValue="20")int size){return ApiResponse.success(service.list(start,end,company,source,status,keyword,page,size));}
    @PostMapping("/sync") public ApiResponse<?> sync(){return ApiResponse.success(service.syncPaidOrders());}
    @PostMapping public ApiResponse<?> create(@RequestBody FinanceTradeService.OfflineInput input){return ApiResponse.success(service.createOffline(input,RequestContext.getUserId()));}
    @GetMapping("/{id}") public ApiResponse<?> detail(@PathVariable UUID id){return ApiResponse.success(service.detail(id));}
    @PostMapping("/{id}/receipts") public ApiResponse<?> receipt(@PathVariable UUID id,@RequestBody FinanceTradeService.ReceiptInput input){return ApiResponse.success(service.recordReceipt(id,input,RequestContext.getUserId()));}
    @PostMapping("/{id}/costs") public ApiResponse<?> costs(@PathVariable UUID id,@RequestBody FinanceTradeService.CostInput input){return ApiResponse.success(service.allocateCosts(id,input,RequestContext.getUserId()));}
    @PostMapping("/{id}/confirm") public ApiResponse<?> confirm(@PathVariable UUID id,@RequestBody VersionInput input){return ApiResponse.success(service.confirm(id,input.version(),RequestContext.getUserId()));}
    @PostMapping("/{id}/final-income") public ApiResponse<?> finalIncome(@PathVariable UUID id,@RequestBody FinanceTradeService.FinalIncomeInput input){return ApiResponse.success(service.finalizeIncome(id,input,RequestContext.getUserId()));}
    @PutMapping("/{id}/referrer") public ApiResponse<?> referrer(@PathVariable UUID id,@RequestBody FinanceTradeService.ReferrerInput input){return ApiResponse.success(service.referrer(id,input,RequestContext.getUserId()));}
    @PostMapping("/import/preview") public ApiResponse<?> preview(@RequestBody List<FinanceTradeService.OfflineInput> input){return ApiResponse.success(service.previewImport(input));}
    @PostMapping("/import/commit") public ApiResponse<?> commit(@RequestBody List<FinanceTradeService.OfflineInput> input){return ApiResponse.success(service.commitImport(input,RequestContext.getUserId()));}
}
