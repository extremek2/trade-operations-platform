package com.tradeoperationsplatform.apiserver.domain.identity.controller;

import com.tradeoperationsplatform.apiserver.domain.identity.dto.*;
import com.tradeoperationsplatform.apiserver.domain.identity.service.IdentityService;
import com.tradeoperationsplatform.apiserver.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {
    private final IdentityService identityService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request) {
        throw new org.springframework.security.access.AccessDeniedException("조직 개설 신청과 관리자 승인을 이용해 주세요.");
    }
}
