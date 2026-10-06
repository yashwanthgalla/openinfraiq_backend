package com.openinfra.backend.controller;

import com.openinfra.backend.dto.AnalysisResponse;
import com.openinfra.backend.dto.AnalysisSaveRequest;
import com.openinfra.backend.dto.ApiResponse;
import com.openinfra.backend.service.AnalysisService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analyses")
@Slf4j
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AnalysisResponse>> saveAnalysis(
            Authentication authentication,
            @RequestHeader(value = "X-Firebase-Uid", required = false) String headerUid,
            @Valid @RequestBody AnalysisSaveRequest request
    ) {
        String uid = null;
        if (authentication != null && authentication.getPrincipal() instanceof String) {
            uid = (String) authentication.getPrincipal();
        } else if (headerUid != null && !headerUid.isBlank()) {
            uid = headerUid;
        }

        AnalysisResponse response = analysisService.saveAnalysis(request, uid);
        return ResponseEntity.ok(ApiResponse.ok("Analysis saved successfully", response));
    }

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<AnalysisResponse>> getLatestAnalysis(
            @RequestParam String owner,
            @RequestParam String name
    ) {
        AnalysisResponse response = analysisService.getLatestAnalysis(owner, name);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AnalysisResponse>> getAnalysisById(@PathVariable Long id) {
        AnalysisResponse response = analysisService.getAnalysisById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
