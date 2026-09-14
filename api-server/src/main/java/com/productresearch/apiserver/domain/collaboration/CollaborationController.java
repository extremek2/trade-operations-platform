package com.productresearch.apiserver.domain.collaboration;

import com.productresearch.apiserver.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;
import static com.productresearch.apiserver.domain.collaboration.CollaborationRequests.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CollaborationController {
    private final CollaborationService service;
    @GetMapping("/shipments/{shipmentId}/partners")
    public ApiResponse<List<Map<String,Object>>> partners(@PathVariable UUID shipmentId) { return ApiResponse.ok(service.partners(shipmentId)); }
    @PostMapping("/shipments/{shipmentId}/partners") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String,Object>> attach(@PathVariable UUID shipmentId,@Valid @RequestBody Attach request) { return ApiResponse.ok(service.attach(shipmentId,request)); }
    @PostMapping("/shipments/{shipmentId}/partners/{partnerId}/invitations") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String,Object>> invite(@PathVariable UUID shipmentId,@PathVariable UUID partnerId,@Valid @RequestBody Invite request) { return ApiResponse.ok(service.invite(shipmentId,partnerId,request)); }
    @PostMapping("/shipments/{shipmentId}/participants/{participantId}/revoke")
    public ApiResponse<Void> revoke(@PathVariable UUID shipmentId,@PathVariable UUID participantId,@Valid @RequestBody Revoke request) {
        service.revoke(shipmentId,participantId,request.reason()); return ApiResponse.ok(null);
    }
}
