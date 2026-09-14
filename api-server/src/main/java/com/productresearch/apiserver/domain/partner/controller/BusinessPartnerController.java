package com.productresearch.apiserver.domain.partner.controller;

import com.productresearch.apiserver.domain.partner.dto.*;
import com.productresearch.apiserver.domain.partner.service.BusinessPartnerService;
import com.productresearch.apiserver.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations/current/partners")
@RequiredArgsConstructor
public class BusinessPartnerController {
    private final BusinessPartnerService service;

    @GetMapping
    public ApiResponse<List<BusinessPartnerResponse>> findAll() {
        return ApiResponse.ok(service.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BusinessPartnerResponse> create(@Valid @RequestBody BusinessPartnerRequests.Create request) {
        return ApiResponse.ok("business partner created", service.create(request));
    }
}
