package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import com.tradeoperationsplatform.apiserver.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trade-cycles")
@RequiredArgsConstructor
public class TradeCycleController {
    private final TradeCycleService service;

    @PostMapping("/purchase-orders")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TradeCycleResponse> createOrder(@Valid @RequestBody TradeCycleRequests.CreatePurchaseOrder request) {
        return ApiResponse.ok("purchase order created", service.createOrder(request));
    }

    @GetMapping("/purchase-orders")
    public ApiResponse<List<TradeCycleResponse>> findAll() { return ApiResponse.ok(service.findAll()); }

    @GetMapping("/purchase-orders/{orderId}")
    public ApiResponse<TradeCycleResponse> findOne(@PathVariable UUID orderId) { return ApiResponse.ok(service.findOne(orderId)); }

    @PostMapping("/purchase-orders/{orderId}/approve")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ApiResponse<TradeCycleResponse> approve(@PathVariable UUID orderId, @Valid @RequestBody TradeCycleRequests.OrderAction request) {
        return ApiResponse.ok(service.approve(orderId, request));
    }

    @PostMapping("/purchase-orders/{orderId}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ApiResponse<TradeCycleResponse> cancel(@PathVariable UUID orderId, @Valid @RequestBody TradeCycleRequests.OrderAction request) {
        return ApiResponse.ok(service.cancel(orderId, request));
    }

    @PostMapping("/purchase-orders/{orderId}/payment")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ApiResponse<TradeCycleResponse> payment(@PathVariable UUID orderId, @Valid @RequestBody TradeCycleRequests.Payment request) {
        return ApiResponse.ok(service.recordPayment(orderId, request));
    }

    @PostMapping("/shipment-allocations")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TradeCycleResponse> allocate(@Valid @RequestBody TradeCycleRequests.AllocateShipment request) {
        return ApiResponse.ok("shipment allocated", service.allocate(request));
    }

    @PostMapping("/receipts")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TradeCycleResponse> receive(@Valid @RequestBody TradeCycleRequests.CreateReceipt request) {
        return ApiResponse.ok("receipt created", service.receive(request));
    }

    @PostMapping("/purchase-orders/{orderId}/actual-costs")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TradeCycleResponse> addActualCost(@PathVariable UUID orderId,
            @Valid @RequestBody TradeCycleRequests.AddActualCost request) {
        return ApiResponse.ok("actual cost created", service.addActualCost(orderId, request));
    }

    @PostMapping("/purchase-orders/{orderId}/cost-close")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ApiResponse<TradeCycleResponse> closeCost(@PathVariable UUID orderId) { return ApiResponse.ok(service.closeCost(orderId)); }

    @PostMapping("/purchase-orders/{orderId}/sales")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TradeCycleResponse> addSales(@PathVariable UUID orderId,
            @Valid @RequestBody TradeCycleRequests.AddSalesObservation request) {
        return ApiResponse.ok("sales observation created", service.addSales(orderId, request));
    }

    @PostMapping("/purchase-orders/{orderId}/decisions")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TradeCycleResponse> addDecision(@PathVariable UUID orderId,
            @Valid @RequestBody TradeCycleRequests.AddDecision request) {
        return ApiResponse.ok("decision created", service.addDecision(orderId, request));
    }
}
