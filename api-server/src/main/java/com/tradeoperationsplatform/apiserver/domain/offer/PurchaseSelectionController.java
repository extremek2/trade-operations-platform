package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class PurchaseSelectionController {
    private final PurchaseSelectionService service;

    @PostMapping("/api/v1/supplier-offer-drafts/{draftId}/selections")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PurchaseSelectionResponse> create(@PathVariable UUID draftId,
            @Valid @RequestBody PurchaseSelectionRequests.Create request) {
        return ApiResponse.ok("purchase selection created", service.create(draftId, request));
    }

    @GetMapping("/api/v1/purchase-selections")
    public ApiResponse<List<PurchaseSelectionResponse>> findAll() { return ApiResponse.ok(service.findAll()); }

    @GetMapping("/api/v1/purchase-selections/{selectionId}")
    public ApiResponse<PurchaseSelectionResponse> findOne(@PathVariable UUID selectionId) {
        return ApiResponse.ok(service.findOne(selectionId));
    }
}
