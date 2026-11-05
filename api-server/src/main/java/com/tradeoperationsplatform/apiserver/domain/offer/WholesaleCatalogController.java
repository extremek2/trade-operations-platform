package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wholesale-catalog")
@RequiredArgsConstructor
public class WholesaleCatalogController {
    private final WholesaleCatalogService service;

    @GetMapping("/status")
    public ApiResponse<WholesaleCatalogService.Status> status() { return ApiResponse.ok(service.status()); }

    @GetMapping("/items")
    public ApiResponse<List<WholesaleCatalogItem>> search(@RequestParam String keyword,
            @RequestParam(defaultValue = "SAMPLE") DomeggookCatalogGateway.Mode mode) {
        return ApiResponse.ok(service.search(keyword, mode));
    }

    @GetMapping("/items/{itemId}")
    public ApiResponse<WholesaleCatalogItem> detail(@PathVariable String itemId,
            @RequestParam(defaultValue = "SAMPLE") DomeggookCatalogGateway.Mode mode) {
        return ApiResponse.ok(service.detail(itemId, mode));
    }

    @PostMapping("/items/{itemId}/offer-draft")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SupplierOfferResponse> importItem(@PathVariable String itemId,
            @RequestParam(defaultValue = "SAMPLE") DomeggookCatalogGateway.Mode mode) {
        return ApiResponse.ok("wholesale offer draft created", service.importItem(itemId, mode));
    }
}
