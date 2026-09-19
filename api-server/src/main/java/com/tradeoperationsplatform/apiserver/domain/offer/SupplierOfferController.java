package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/v1/supplier-offer-drafts")
@RequiredArgsConstructor
public class SupplierOfferController {
    private final SupplierOfferService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SupplierOfferResponse> create(@Valid @RequestBody SupplierOfferRequests.Create request) {
        return ApiResponse.ok("supplier offer draft created", service.create(request));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SupplierOfferResponse> importFile(@RequestParam UUID supplierId,
                                                         @RequestParam String currency,
                                                         @RequestParam(required = false) String sourceReference,
                                                         @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok("supplier offer file imported",
                service.importFile(supplierId, currency, sourceReference, file));
    }

    @GetMapping
    public ApiResponse<List<SupplierOfferResponse>> findAll() { return ApiResponse.ok(service.findAll()); }

    @GetMapping("/{draftId}")
    public ApiResponse<SupplierOfferResponse> findOne(@PathVariable UUID draftId) {
        return ApiResponse.ok(service.findOne(draftId));
    }

    @PutMapping("/{draftId}/lines/{lineNumber}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ApiResponse<SupplierOfferResponse> reviewLine(@PathVariable UUID draftId, @PathVariable int lineNumber,
                                                          @Valid @RequestBody SupplierOfferRequests.ReviewLine request) {
        return ApiResponse.ok(service.reviewLine(draftId, lineNumber, request));
    }

    @PostMapping("/{draftId}/lines/{lineNumber}/exclude")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ApiResponse<SupplierOfferResponse> excludeLine(@PathVariable UUID draftId, @PathVariable int lineNumber,
                                                            @Valid @RequestBody SupplierOfferRequests.Action request) {
        return ApiResponse.ok(service.excludeLine(draftId, lineNumber, request));
    }

    @PostMapping("/{draftId}/confirm")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ApiResponse<SupplierOfferResponse> confirm(@PathVariable UUID draftId,
                                                       @Valid @RequestBody SupplierOfferRequests.Action request) {
        return ApiResponse.ok(service.confirm(draftId, request));
    }
}
