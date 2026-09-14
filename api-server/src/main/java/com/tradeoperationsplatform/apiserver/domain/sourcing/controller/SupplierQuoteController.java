package com.tradeoperationsplatform.apiserver.domain.sourcing.controller;

import com.tradeoperationsplatform.apiserver.domain.sourcing.dto.*;
import com.tradeoperationsplatform.apiserver.domain.sourcing.service.SupplierQuoteService;
import com.tradeoperationsplatform.apiserver.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/quotes")
@RequiredArgsConstructor
public class SupplierQuoteController {
    private final SupplierQuoteService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuoteResponse> create(@Valid @RequestBody QuoteRequests.Create request) {
        return ApiResponse.ok("quote created", service.create(request));
    }

    @GetMapping
    public ApiResponse<List<QuoteResponse>> findAll() { return ApiResponse.ok(service.findAll()); }

    @GetMapping("/{quoteId}")
    public ApiResponse<QuoteResponse> findOne(@PathVariable UUID quoteId) {
        return ApiResponse.ok(service.findOne(quoteId));
    }

    @PutMapping("/{quoteId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ApiResponse<QuoteResponse> revise(@PathVariable UUID quoteId,
                                             @Valid @RequestBody QuoteRequests.Revise request) {
        return ApiResponse.ok(service.revise(quoteId, request));
    }

    @PostMapping("/{quoteId}/revisions")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuoteResponse> createRevision(@PathVariable UUID quoteId,
                                                      @Valid @RequestBody QuoteRequests.CreateRevision request) {
        return ApiResponse.ok("quote revision created", service.createRevision(quoteId, request));
    }

    @PostMapping("/{quoteId}/status")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ApiResponse<QuoteResponse> changeStatus(@PathVariable UUID quoteId,
                                                   @Valid @RequestBody QuoteRequests.ChangeStatus request) {
        return ApiResponse.ok(service.changeStatus(quoteId, request));
    }
}
