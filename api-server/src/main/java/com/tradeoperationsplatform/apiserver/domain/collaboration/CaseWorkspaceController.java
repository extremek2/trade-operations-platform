package com.tradeoperationsplatform.apiserver.domain.collaboration;

import com.tradeoperationsplatform.apiserver.domain.shipment.dto.*;
import com.tradeoperationsplatform.apiserver.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;

@RestController
@RequestMapping("/api/v1/case-workspace/{shipmentId}")
@RequiredArgsConstructor
public class CaseWorkspaceController {
    private final CaseWorkspaceService service;
    @GetMapping public ApiResponse<Map<String,Object>> read(@PathVariable UUID shipmentId) { return ApiResponse.ok(service.read(shipmentId)); }
    @PostMapping("/documents") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransportDocumentResponse> add(@PathVariable UUID shipmentId,@Valid @RequestBody CreateTransportDocumentRequest request) {
        return ApiResponse.ok(service.addDocument(shipmentId,request));
    }
}
