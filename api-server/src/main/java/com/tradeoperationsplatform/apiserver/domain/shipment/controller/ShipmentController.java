package com.tradeoperationsplatform.apiserver.domain.shipment.controller;

import com.tradeoperationsplatform.apiserver.domain.shipment.dto.*;
import com.tradeoperationsplatform.apiserver.domain.shipment.entity.ShipmentCase;
import com.tradeoperationsplatform.apiserver.domain.shipment.service.ShipmentService;
import com.tradeoperationsplatform.apiserver.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.*;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
public class ShipmentController {
    private final ShipmentService shipmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShipmentResponse> create(@Valid @RequestBody CreateShipmentRequest request) {
        return ApiResponse.ok("shipment created", shipmentService.create(request));
    }

    @GetMapping
    public ApiResponse<List<ShipmentResponse>> findAll(
            @RequestParam(required = false) ShipmentCase.Status status,
            @RequestParam(required = false) ShipmentCase.Priority priority,
            @RequestParam(required = false) ShipmentCase.Stage stage,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return ApiResponse.ok(shipmentService.findAll(status, priority, stage, includeArchived));
    }

    @GetMapping("/{shipmentId}")
    public ApiResponse<ShipmentResponse> findOne(@PathVariable UUID shipmentId) {
        return ApiResponse.ok(shipmentService.findOne(shipmentId));
    }

    @PatchMapping("/{shipmentId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ApiResponse<ShipmentResponse> update(@PathVariable UUID shipmentId,
                                                @Valid @RequestBody UpdateShipmentRequest request) {
        return ApiResponse.ok(shipmentService.update(shipmentId, request));
    }

    @PostMapping("/{shipmentId}/archive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ApiResponse<ShipmentResponse> archive(@PathVariable UUID shipmentId) {
        return ApiResponse.ok(shipmentService.archive(shipmentId));
    }

    @PostMapping("/{shipmentId}/documents")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransportDocumentResponse> addDocument(@PathVariable UUID shipmentId,
                                                               @Valid @RequestBody CreateTransportDocumentRequest request) {
        return ApiResponse.ok("document created", shipmentService.addDocument(shipmentId, request));
    }

    @GetMapping("/{shipmentId}/documents")
    public ApiResponse<List<TransportDocumentResponse>> findDocuments(@PathVariable UUID shipmentId) {
        return ApiResponse.ok(shipmentService.findDocuments(shipmentId));
    }
}
