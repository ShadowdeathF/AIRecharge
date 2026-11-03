package com.orionkey.finance;

import com.orionkey.common.ApiResponse;
import com.orionkey.context.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/admin/procurement")
@RequiredArgsConstructor
public class AdminProcurementController {
    private final ProcurementService service;
    private final ProcurementReceivingService receiving;
    @GetMapping("/purchases") public ApiResponse<?> list(@RequestParam(defaultValue="1") int page,@RequestParam(name="page_size",defaultValue="20") int size) {return ApiResponse.success(service.list(page,size));}
    @GetMapping("/purchases/{id}") public ApiResponse<?> detail(@PathVariable UUID id){return ApiResponse.success(service.detail(id));}
    @PostMapping("/purchases") public ApiResponse<?> create(@RequestBody ProcurementService.PurchaseInput input){return ApiResponse.success(service.createPurchase(input,RequestContext.getUserId()));}
    @PostMapping("/purchases/{id}/receipts") public ApiResponse<?> receive(@PathVariable UUID id,@RequestBody ProcurementService.ReceiveInput input){return ApiResponse.success(service.receive(id,input,RequestContext.getUserId()));}
    @PostMapping("/purchases/{id}/receipts-with-cards") public ApiResponse<?> receiveCards(@PathVariable UUID id,@RequestBody ProcurementReceivingService.Input input){return ApiResponse.success(receiving.receive(id,input,RequestContext.getUserId()));}
    @GetMapping("/lots") public ApiResponse<?> lots(){return ApiResponse.success(service.stock());}
    @PostMapping("/lots/{id}/card-batch/{batchId}") public ApiResponse<?> link(@PathVariable UUID id,@PathVariable UUID batchId){service.linkCardBatch(id,batchId,RequestContext.getUserId());return ApiResponse.success();}
}
