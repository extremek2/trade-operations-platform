package com.tradeoperationsplatform.apiserver.domain.auth.controller;

import com.tradeoperationsplatform.apiserver.domain.auth.dto.*;
import com.tradeoperationsplatform.apiserver.domain.auth.service.AuthService;
import com.tradeoperationsplatform.apiserver.global.exception.BusinessException;
import com.tradeoperationsplatform.apiserver.global.exception.AuthenticationFailedException;
import com.tradeoperationsplatform.apiserver.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final String REFRESH_COOKIE = "trade_ops_refresh";
    private final AuthService authService;
    private final com.tradeoperationsplatform.apiserver.domain.collaboration.CollaborationService collaboration;
    private final com.tradeoperationsplatform.apiserver.domain.onboarding.service.VerificationRateLimiter rateLimiter;
    @Value("${app.auth.refresh-token-days}") private long refreshDays;
    @Value("${app.auth.secure-cookie}") private boolean secureCookie;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<AuthResponse>> signup(@Valid @RequestBody SignupRequest request, jakarta.servlet.http.HttpServletRequest http) {
        if (!rateLimiter.allowed("signup:" + http.getRemoteAddr())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요.");
        return response(authService.signup(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return response(authService.login(request), HttpStatus.OK);
    }

    @PostMapping("/case-links/request")
    public ApiResponse<Void> caseLink(@Valid @RequestBody com.tradeoperationsplatform.apiserver.domain.collaboration.CollaborationRequests.RequestLink request, jakarta.servlet.http.HttpServletRequest http) {
        caseRateLimit(http);
        collaboration.requestLink(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/case-links/confirm")
    public ResponseEntity<ApiResponse<AuthResponse>> confirmCase(@Valid @RequestBody com.tradeoperationsplatform.apiserver.domain.collaboration.CollaborationRequests.Confirm request, jakarta.servlet.http.HttpServletRequest http) {
        caseRateLimit(http);
        return response(collaboration.confirm(request.token()), HttpStatus.OK);
    }

    private void caseRateLimit(jakarta.servlet.http.HttpServletRequest http) {
        if (!rateLimiter.allowed("case-link:" + http.getRemoteAddr()))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"잠시 후 다시 시도해 주세요.");
    }

    @PostMapping("/platform/login")
    public ResponseEntity<ApiResponse<AuthResponse>> platformLogin(@Valid @RequestBody LoginRequest request) {
        return response(authService.platformLogin(request), HttpStatus.OK);
    }

    @PostMapping("/context")
    public ResponseEntity<ApiResponse<AuthResponse>> context(@Valid @RequestBody ContextRequest request) {
        return response(authService.switchContext(request), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
        if (token == null) throw new AuthenticationFailedException("Refresh Token이 없습니다.");
        return response(authService.refresh(token), HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
        if (token != null) authService.logout(token);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("", 0).toString()).body(ApiResponse.ok(null));
    }

    @GetMapping("/me")
    public ApiResponse<AuthResponse.UserContext> me() { return ApiResponse.ok(authService.me()); }

    private ResponseEntity<ApiResponse<AuthResponse>> response(AuthService.Tokens tokens, HttpStatus status) {
        return ResponseEntity.status(status).header(HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), refreshDays * 86400).toString())
                .body(ApiResponse.ok(tokens.response()));
    }
    private ResponseCookie cookie(String value, long maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true).secure(secureCookie).sameSite("Strict")
                .path("/api/v1/auth").maxAge(maxAge).build();
    }
}
