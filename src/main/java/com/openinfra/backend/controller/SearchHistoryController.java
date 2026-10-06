package com.openinfra.backend.controller;

import com.openinfra.backend.dto.ApiResponse;
import com.openinfra.backend.dto.SearchHistoryRequest;
import com.openinfra.backend.dto.SearchHistoryResponse;
import com.openinfra.backend.security.FirebaseUserPrincipal;
import com.openinfra.backend.service.SearchHistoryService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@Slf4j
public class SearchHistoryController {

    private final SearchHistoryService searchHistoryService;

    public SearchHistoryController(SearchHistoryService searchHistoryService) {
        this.searchHistoryService = searchHistoryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SearchHistoryResponse>>> getUserHistory(
            @AuthenticationPrincipal FirebaseUserPrincipal principal
    ) {
        List<SearchHistoryResponse> history = searchHistoryService.getUserHistory(principal.getFirebaseUid());
        return ResponseEntity.ok(ApiResponse.ok(history));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SearchHistoryResponse>> recordSearch(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @Valid @RequestBody SearchHistoryRequest request
    ) {
        SearchHistoryResponse response = searchHistoryService.recordSearch(principal.getFirebaseUid(), request);
        return ResponseEntity.ok(ApiResponse.ok("Search recorded successfully", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteHistoryItem(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable Long id
    ) {
        searchHistoryService.deleteHistoryItem(principal.getFirebaseUid(), id);
        return ResponseEntity.ok(ApiResponse.ok("History item deleted", null));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearHistory(
            @AuthenticationPrincipal FirebaseUserPrincipal principal
    ) {
        searchHistoryService.clearUserHistory(principal.getFirebaseUid());
        return ResponseEntity.ok(ApiResponse.ok("Search history cleared", null));
    }
}
