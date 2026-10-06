package com.openinfra.backend.controller;

import com.openinfra.backend.dto.AnalyzeRepoRequest;
import com.openinfra.backend.dto.ApiResponse;
import com.openinfra.backend.dto.RepositoryResponse;
import com.openinfra.backend.entity.RepositoryAnalysis;
import com.openinfra.backend.security.FirebaseUserPrincipal;
import com.openinfra.backend.service.RepositoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/repositories")
@Slf4j
public class RepositoryController {

    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeRepository(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @RequestBody AnalyzeRepoRequest request
    ) {
        String owner = request.getOwner();
        String name = request.getName();

        // Support URL parsing if only URL provided
        if ((owner == null || owner.isBlank() || name == null || name.isBlank())) {
            String url = request.getRepositoryUrl() != null ? request.getRepositoryUrl() : request.getUrl();
            if (url != null && !url.isBlank()) {
                String cleaned = url.replace("https://github.com/", "")
                        .replace("http://github.com/", "")
                        .replaceAll("^/+", "")
                        .replaceAll("/+$", "")
                        .replace(".git", "");
                String[] parts = cleaned.split("/");
                if (parts.length >= 2) {
                    owner = parts[0];
                    name = parts[1];
                }
            }
        }

        if (owner == null || owner.isBlank() || name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Repository owner and name are required"));
        }

        Map<String, Object> result = repositoryService.analyzeAndPersist(principal.getUser(), owner, name);
        return ResponseEntity.ok(ApiResponse.ok("Repository analyzed and saved successfully", result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<RepositoryResponse>>> getUserRepositories(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<RepositoryResponse> page = repositoryService.getUserRepositories(principal.getUser(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RepositoryResponse>> getRepositoryById(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable Long id
    ) {
        RepositoryResponse repo = repositoryService.getRepositoryById(principal.getUser(), id);
        return ResponseEntity.ok(ApiResponse.ok(repo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRepository(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable Long id
    ) {
        repositoryService.deleteRepositoryById(principal.getUser(), id);
        return ResponseEntity.ok(ApiResponse.ok("Repository removed from user workspace", null));
    }

    @GetMapping("/{id}/analyses")
    public ResponseEntity<ApiResponse<Page<RepositoryAnalysis>>> getRepositoryAnalyses(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable Long id,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Page<RepositoryAnalysis> analyses = repositoryService.getRepositoryAnalyses(principal.getUser(), id, pageable);
        return ResponseEntity.ok(ApiResponse.ok(analyses));
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reanalyzeRepository(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable Long id
    ) {
        RepositoryResponse repo = repositoryService.getRepositoryById(principal.getUser(), id);
        Map<String, Object> result = repositoryService.analyzeAndPersist(
                principal.getUser(), repo.getOwner(), repo.getName()
        );
        return ResponseEntity.ok(ApiResponse.ok("Repository re-analyzed successfully", result));
    }
}
