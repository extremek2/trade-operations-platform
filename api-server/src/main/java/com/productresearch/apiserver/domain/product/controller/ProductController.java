package com.productresearch.apiserver.domain.product.controller;

import com.productresearch.apiserver.domain.product.dto.*;
import com.productresearch.apiserver.domain.product.service.ProductService;
import com.productresearch.apiserver.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Product", description = "상품 API")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "상품 후보 등록")
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequests.Create request) {
        return ApiResponse.ok("product created", productService.create(request));
    }

    @GetMapping
    @Operation(summary = "조직 상품 조회")
    public ApiResponse<List<ProductResponse>> findAll() {
        return ApiResponse.ok(productService.findAll());
    }

    @GetMapping("/{productId}")
    @Operation(summary = "상품 단건 조회")
    public ApiResponse<ProductResponse> findOne(@PathVariable UUID productId) {
        return ApiResponse.ok(productService.findOne(productId));
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ApiResponse<ProductResponse> revise(@PathVariable UUID productId,
                                               @Valid @RequestBody ProductRequests.Revise request) {
        return ApiResponse.ok(productService.revise(productId, request));
    }

    @PostMapping("/{productId}/status")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    public ApiResponse<ProductResponse> changeStatus(@PathVariable UUID productId,
                                                      @Valid @RequestBody ProductRequests.ChangeStatus request) {
        return ApiResponse.ok(productService.changeStatus(productId, request));
    }
}
