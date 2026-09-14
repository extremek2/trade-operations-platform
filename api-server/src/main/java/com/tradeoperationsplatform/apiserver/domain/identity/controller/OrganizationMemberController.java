package com.tradeoperationsplatform.apiserver.domain.identity.controller;

import com.tradeoperationsplatform.apiserver.domain.identity.dto.*;
import com.tradeoperationsplatform.apiserver.domain.identity.service.OrganizationMemberService;
import com.tradeoperationsplatform.apiserver.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/current/members")
@RequiredArgsConstructor
public class OrganizationMemberController {
    private final OrganizationMemberService service;

    @GetMapping
    public ApiResponse<Page<MemberResponse>> list(@RequestParam(defaultValue="0") int page,
                                                   @RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(service.list(page, size));
    }

    @PatchMapping("/{userId}")
    public ApiResponse<MemberResponse> change(@PathVariable UUID userId, @Valid @RequestBody ChangeMemberRequest request) {
        return ApiResponse.ok(service.change(userId, request));
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ApiResponse<MemberResponse> add(@Valid @RequestBody AddMemberRequest request) {
        return ApiResponse.ok(service.add(request));
    }
}
