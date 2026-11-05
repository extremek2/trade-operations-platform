package com.tradeoperationsplatform.apiserver.domain.onboarding.controller;

import com.tradeoperationsplatform.apiserver.domain.onboarding.service.*;
import com.tradeoperationsplatform.apiserver.domain.onboarding.dto.*;
import com.tradeoperationsplatform.apiserver.domain.onboarding.entity.OrganizationApplication.Status;
import com.tradeoperationsplatform.apiserver.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OnboardingController {
    private final OrganizationApplicationService applications;
    private final EmailVerificationService verification;
    private final VerificationRateLimiter rateLimiter;
    public record ConfirmRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token) {}

    @PostMapping("/organization-applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApplicationResponse> submit(@Valid @RequestBody ApplicationRequest request) { return ApiResponse.ok(applications.submit(request)); }
    @GetMapping("/me/applications")
    public ApiResponse<Page<ApplicationResponse>> mine(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return ApiResponse.ok(applications.mine(page,size)); }
    @PostMapping("/auth/email-verifications/request")
    public ApiResponse<Void> request() { verification.request(); return ApiResponse.ok(null); }
    @PostMapping("/auth/email-verifications/confirm")
    public ApiResponse<Void> confirm(@Valid @RequestBody ConfirmRequest request, HttpServletRequest http) {
        if (!rateLimiter.allowed(http.getRemoteAddr())) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요.");
        verification.confirm(request.token()); return ApiResponse.ok(null);
    }
    @GetMapping("/system-admin/applications")
    public ApiResponse<Page<ApplicationResponse>> queue(@RequestParam(required=false) Status status,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return ApiResponse.ok(applications.queue(status,page,size)); }
    @GetMapping("/system-admin/applications/{id}")
    public ApiResponse<ApplicationResponse.AdminDetail> detail(@PathVariable UUID id) { return ApiResponse.ok(applications.detail(id)); }
    @PostMapping("/system-admin/applications/{id}/approve")
    public ApiResponse<ApplicationResponse.AdminDetail> approve(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request) { return ApiResponse.ok(applications.decide(id,request,true)); }
    @PostMapping("/system-admin/applications/{id}/reject")
    public ApiResponse<ApplicationResponse.AdminDetail> reject(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request) { return ApiResponse.ok(applications.decide(id,request,false)); }
}
