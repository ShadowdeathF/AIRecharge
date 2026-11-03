package com.orionkey.finance;
import com.orionkey.common.ApiResponse;
import com.orionkey.context.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/admin/finance") @RequiredArgsConstructor
public class AdminFinanceSettlementController {
    private final FinanceSettlementService service;
    @GetMapping("/overview") public ApiResponse<?> overview(){return ApiResponse.success(service.overview());}
    @GetMapping("/people") public ApiResponse<?> people(){return ApiResponse.success(service.people());}
    @PostMapping("/people") public ApiResponse<?> createPerson(@RequestBody FinanceSettlementService.PersonInput input){return ApiResponse.success(service.savePerson(null,input,RequestContext.getUserId()));}
    @PutMapping("/people/{id}") public ApiResponse<?> savePerson(@PathVariable UUID id,@RequestBody FinanceSettlementService.PersonInput input){return ApiResponse.success(service.savePerson(id,input,RequestContext.getUserId()));}
    @GetMapping("/candidates") public ApiResponse<?> candidates(){return ApiResponse.success(service.candidates());}
    @PostMapping("/settlements/preview") public ApiResponse<?> preview(@RequestBody FinanceSettlementService.ProfitInput input){return ApiResponse.success(service.previewProfit(input));}
    @PostMapping("/settlements") public ApiResponse<?> settle(@RequestBody FinanceSettlementService.ProfitInput input){return ApiResponse.success(service.confirmProfit(input,RequestContext.getUserId()));}
    @GetMapping("/settlements") public ApiResponse<?> documents(@RequestParam(defaultValue="1")int page,@RequestParam(name="page_size",defaultValue="20")int size){return ApiResponse.success(service.documents(page,size));}
    @GetMapping("/settlements/{id}") public ApiResponse<?> detail(@PathVariable UUID id){return ApiResponse.success(service.detail(id));}
    @GetMapping("/quarters/{quarter}/preview") public ApiResponse<?> quarter(@PathVariable String quarter){return ApiResponse.success(service.previewQuarter(quarter));}
    @PostMapping("/quarters") public ApiResponse<?> quarterConfirm(@RequestBody FinanceSettlementService.QuarterInput input){return ApiResponse.success(service.confirmQuarter(input,RequestContext.getUserId()));}
    @PostMapping("/adjustments") public ApiResponse<?> adjust(@RequestBody FinanceSettlementService.AdjustInput input){return ApiResponse.success(service.adjust(input,RequestContext.getUserId()));}
    @GetMapping("/trades/{id}/adjustments") public ApiResponse<?> adjustments(@PathVariable UUID id){return ApiResponse.success(service.tradeAdjustments(id));}
    @PostMapping("/cash") public ApiResponse<?> cash(@RequestBody FinanceSettlementService.CashInput input){return ApiResponse.success(service.recordCash(input,RequestContext.getUserId()));}
}
