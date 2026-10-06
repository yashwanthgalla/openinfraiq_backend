package com.openinfra.backend.controller;

import com.openinfra.backend.dto.ApiResponse;
import com.openinfra.backend.dto.SavedRepositoryRequest;
import com.openinfra.backend.dto.SavedRepositoryResponse;
import com.openinfra.backend.security.FirebaseUserPrincipal;
import com.openinfra.backend.service.SavedRepositoryService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/saved")
@Slf4j
public class SavedRepositoryController {

    private final SavedRepositoryService savedRepositoryService;

    public SavedRepositoryController(SavedRepositoryService savedRepositoryService) {
        this.savedRepositoryService = savedRepositoryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SavedRepositoryResponse>>> getUserSaved(
            @AuthenticationPrincipal FirebaseUserPrincipal principal
    ) {
        List<SavedRepositoryResponse> list = savedRepositoryService.getUserSavedRepositories(principal.getFirebaseUid());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/toggle")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleBookmark(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @Valid @RequestBody SavedRepositoryRequest request
    ) {
        boolean isSaved = savedRepositoryService.toggleBookmark(principal.getFirebaseUid(), request);
        return ResponseEntity.ok(ApiResponse.ok("Bookmark updated", Map.of(
                "saved", isSaved,
                "owner", request.getOwner(),
                "repositoryName", request.getRepositoryName()
        )));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSaved(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable Long id
    ) {
        savedRepositoryService.deleteSavedRepository(principal.getFirebaseUid(), id);
        return ResponseEntity.ok(ApiResponse.ok("Saved repository deleted", null));
    }

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkIfSaved(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @RequestParam String owner,
            @RequestParam String name
    ) {
        boolean saved = savedRepositoryService.isSaved(principal.getFirebaseUid(), owner, name);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("saved", saved)));
    }
}
