package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/supplier-documents")
@RequiredArgsConstructor
public class SupplierDocumentController {
    private final SupplierDocumentService service;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SupplierDocumentResponse> importDocument(@RequestParam UUID supplierId,
                                                                 @RequestParam(required = false) String sourceReference,
                                                                 @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok("supplier document archived and extraction attempted",
                service.importDocument(supplierId, sourceReference, file));
    }

    @GetMapping
    public ApiResponse<List<SupplierDocumentResponse>> findAll() { return ApiResponse.ok(service.findAll()); }

    @GetMapping("/{documentId}")
    public ApiResponse<SupplierDocumentResponse> findOne(@PathVariable UUID documentId) {
        return ApiResponse.ok(service.findOne(documentId));
    }

    @GetMapping("/{documentId}/content")
    public ResponseEntity<byte[]> download(@PathVariable UUID documentId) {
        SupplierDocumentService.Download download = service.download(documentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.content().length)
                .body(download.content());
    }
}
