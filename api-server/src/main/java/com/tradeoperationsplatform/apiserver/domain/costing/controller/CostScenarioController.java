package com.tradeoperationsplatform.apiserver.domain.costing.controller;

import com.tradeoperationsplatform.apiserver.domain.costing.dto.CostScenarioRequests;
import com.tradeoperationsplatform.apiserver.domain.costing.dto.CostScenarioResponse;
import com.tradeoperationsplatform.apiserver.domain.costing.service.CostScenarioService;
import com.tradeoperationsplatform.apiserver.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cost-scenarios")
@RequiredArgsConstructor
public class CostScenarioController {
    private final CostScenarioService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CostScenarioResponse> create(@Valid @RequestBody CostScenarioRequests.Create request) {
        return ApiResponse.ok("cost scenario created", service.create(request));
    }

    @GetMapping
    public ApiResponse<List<CostScenarioResponse>> findAll() {
        return ApiResponse.ok(service.findAll());
    }

    @GetMapping("/{scenarioId}")
    public ApiResponse<CostScenarioResponse> findOne(@PathVariable UUID scenarioId) {
        return ApiResponse.ok(service.findOne(scenarioId));
    }
}
