package com.openinfra.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openinfra.backend.dto.*;
import com.openinfra.backend.entity.ProjectRequirement;
import com.openinfra.backend.entity.Repository;
import com.openinfra.backend.entity.RepositoryAnalysis;
import com.openinfra.backend.entity.User;
import com.openinfra.backend.exception.ResourceNotFoundException;
import com.openinfra.backend.repository.ProjectRequirementRepository;
import com.openinfra.backend.repository.RepositoryAnalysisRepository;
import com.openinfra.backend.repository.RepositoryRepository;
import com.openinfra.backend.repository.SearchHistoryRepository;
import com.openinfra.backend.repository.UserRepository;
import com.openinfra.backend.security.FirebaseUserPrincipal;
import com.openinfra.backend.service.*;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ai")
@Slf4j
public class AIController {

    private final AIAnalysisService aiAnalysisService;
    private final AIRequirementService aiRequirementService;
    private final AIRepositoryMatchingService aiRepositoryMatchingService;
    private final RepositoryRepository repositoryRepository;
    private final RepositoryAnalysisRepository analysisRepository;
    private final GitHubService gitHubService;
    private final ProjectRequirementRepository projectRequirementRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AIController(AIAnalysisService aiAnalysisService,
                        AIRequirementService aiRequirementService,
                        AIRepositoryMatchingService aiRepositoryMatchingService,
                        RepositoryRepository repositoryRepository,
                        RepositoryAnalysisRepository analysisRepository,
                        GitHubService gitHubService,
                        ProjectRequirementRepository projectRequirementRepository,
                        SearchHistoryRepository searchHistoryRepository,
                        UserRepository userRepository,
                        ObjectMapper objectMapper) {
        this.aiAnalysisService = aiAnalysisService;
        this.aiRequirementService = aiRequirementService;
        this.aiRepositoryMatchingService = aiRepositoryMatchingService;
        this.repositoryRepository = repositoryRepository;
        this.analysisRepository = analysisRepository;
        this.gitHubService = gitHubService;
        this.projectRequirementRepository = projectRequirementRepository;
        this.searchHistoryRepository = searchHistoryRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Feature A: Generate or retrieve qualitative AI Intelligence for an analyzed repository.
     * Stored in MySQL (RepositoryAnalysis and SearchHistory).
     */
    @PostMapping("/repository/{repositoryId}/analyze")
    public ResponseEntity<ApiResponse<AIInsightsResponse>> getOrGenerateAiInsights(
            @PathVariable Long repositoryId,
            @AuthenticationPrincipal FirebaseUserPrincipal principal
    ) {
        if (principal == null || principal.getUser() == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required to access AI Repository Intelligence. Please log in or create an account."));
        }

        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found with ID: " + repositoryId));

        RepositoryAnalysis latestAnalysis = analysisRepository.findTopByRepositoryOrderByAnalyzedAtDesc(repo)
                .orElse(null);

        // Check if pre-computed AI insights already exist in analysis
        AIInsightsResponse insights = null;
        if (latestAnalysis != null && latestAnalysis.getAiInsightsJson() != null && !latestAnalysis.getAiInsightsJson().isBlank()) {
            try {
                insights = objectMapper.readValue(latestAnalysis.getAiInsightsJson(), AIInsightsResponse.class);
            } catch (Exception e) {
                log.debug("Failed to deserialize existing AI insights json, regenerating: {}", e.getMessage());
            }
        }

        // Generate on-demand if not cached
        if (insights == null) {
            Map<String, Object> analysisMap = null;
            if (latestAnalysis != null && latestAnalysis.getRawAnalysisJson() != null) {
                try {
                    analysisMap = objectMapper.readValue(latestAnalysis.getRawAnalysisJson(), Map.class);
                } catch (Exception ignored) {}
            }

            List<GitHubService.RawGitHubIssue> issues = gitHubService.fetchRecentIssues(repo.getOwner(), repo.getName());
            List<GitHubService.RawGitHubPull> pulls = gitHubService.fetchRecentPulls(repo.getOwner(), repo.getName());

            insights = aiAnalysisService.generateRepositoryInsights(repo, analysisMap, issues, pulls);

            if (latestAnalysis != null) {
                aiAnalysisService.attachAiInsightsToAnalysis(latestAnalysis, insights);
            }
        }

        // Persist/record user search history in MySQL
        try {
            User user = principal.getUser();
            Optional<com.openinfra.backend.entity.SearchHistory> existingHistory = searchHistoryRepository.findByUserAndRepository(user, repo);
            if (existingHistory.isPresent()) {
                com.openinfra.backend.entity.SearchHistory hist = existingHistory.get();
                hist.setViewCount((hist.getViewCount() != null ? hist.getViewCount() : 1) + 1);
                hist.setLastViewedAt(java.time.LocalDateTime.now());
                if (latestAnalysis != null) hist.setLatestAnalysis(latestAnalysis);
                searchHistoryRepository.save(hist);
            } else {
                com.openinfra.backend.entity.SearchHistory newHist = com.openinfra.backend.entity.SearchHistory.builder()
                        .user(user)
                        .repository(repo)
                        .searchQuery(repo.getFullName())
                        .statusSnapshot(latestAnalysis != null ? latestAnalysis.getAdoptionRecommendation() : "ANALYZED")
                        .latestAnalysis(latestAnalysis)
                        .viewCount(1)
                        .searchedAt(java.time.LocalDateTime.now())
                        .lastViewedAt(java.time.LocalDateTime.now())
                        .build();
                searchHistoryRepository.save(newHist);
            }
            log.info("Recorded AI repository analysis view in MySQL for user: {}", user.getFirebaseUid());
        } catch (Exception e) {
            log.warn("Failed to record SearchHistory in MySQL: {}", e.getMessage());
        }

        return ResponseEntity.ok(ApiResponse.ok(insights));
    }

    /**
     * Feature B: Convert natural language project requirements into structured specifications.
     * Auto-saved to MySQL for the logged-in user.
     */
    @PostMapping("/requirements/analyze")
    public ResponseEntity<ApiResponse<AIRequirementResponse>> analyzeRequirements(
            @Valid @RequestBody AIRequirementRequest request,
            @AuthenticationPrincipal FirebaseUserPrincipal principal
    ) {
        if (principal == null || principal.getUser() == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required to use AI Project Finder. Please log in or create an account."));
        }

        AIRequirementResponse response = aiRequirementService.extractRequirements(request);

        // Auto-save parsed requirement search to MySQL
        try {
            ProjectRequirement pr = ProjectRequirement.builder()
                    .user(principal.getUser())
                    .title(response.getSummaryText() != null ? response.getSummaryText() : request.getDescription())
                    .description(request.getDescription())
                    .category(response.getCategory())
                    .structuredRequirementsJson(objectMapper.writeValueAsString(response))
                    .build();
            projectRequirementRepository.save(pr);
            log.info("Auto-saved AI project requirement search to MySQL for user: {}", principal.getUser().getFirebaseUid());
        } catch (Exception e) {
            log.warn("Failed to auto-save AI requirement to MySQL: {}", e.getMessage());
        }

        return ResponseEntity.ok(ApiResponse.ok("Requirements understood successfully", response));
    }

    /**
     * Feature B: Discover and match candidate GitHub repositories against requirements.
     * Search and candidate matches are auto-saved to MySQL for the logged-in user.
     */
    @PostMapping("/requirements/search")
    public ResponseEntity<ApiResponse<List<AIRepositoryMatchResponse>>> searchAndMatch(
            @RequestBody AIRequirementResponse requirements,
            @RequestParam(defaultValue = "6") int limit,
            @AuthenticationPrincipal FirebaseUserPrincipal principal
    ) {
        if (principal == null || principal.getUser() == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required to search repositories with AI. Please log in or create an account."));
        }

        if (requirements.getKeywords() == null || requirements.getKeywords().isEmpty()) {
            if (requirements.getRawQuery() != null && !requirements.getRawQuery().isBlank()) {
                requirements = aiRequirementService.extractRequirements(
                        new AIRequirementRequest(requirements.getRawQuery(), null)
                );
            }
        }

        List<AIRepositoryMatchResponse> matches = aiRepositoryMatchingService.findAndMatchRepositories(requirements, limit);

        // Auto-save search and matched results to MySQL for this user
        try {
            String structuredJson = objectMapper.writeValueAsString(requirements);
            String matchesJson = objectMapper.writeValueAsString(matches);

            String queryText = requirements.getRawQuery() != null && !requirements.getRawQuery().isBlank()
                    ? requirements.getRawQuery()
                    : (requirements.getSummaryText() != null ? requirements.getSummaryText() : "AI Repository Search");

            ProjectRequirement pr = ProjectRequirement.builder()
                    .user(principal.getUser())
                    .title(requirements.getSummaryText() != null ? requirements.getSummaryText() : queryText)
                    .description(queryText)
                    .category(requirements.getCategory())
                    .structuredRequirementsJson(structuredJson)
                    .matchesJson(matchesJson)
                    .build();
            projectRequirementRepository.save(pr);
            log.info("Auto-persisted AI search results and {} matches into MySQL for user: {}", matches.size(), principal.getUser().getFirebaseUid());
        } catch (Exception e) {
            log.warn("Failed to persist AI search results to MySQL: {}", e.getMessage());
        }

        return ResponseEntity.ok(ApiResponse.ok(matches));
    }

    /**
     * Feature B: Match a single repository against specific requirements.
     */
    @PostMapping("/repository/{repositoryId}/match")
    public ResponseEntity<ApiResponse<AIRepositoryMatchResponse>> matchRepository(
            @PathVariable Long repositoryId,
            @RequestBody AIRequirementResponse requirements,
            @AuthenticationPrincipal FirebaseUserPrincipal principal
    ) {
        if (principal == null || principal.getUser() == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required to match repository with AI. Please log in or create an account."));
        }

        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found with ID: " + repositoryId));

        GitHubService.RawGitHubRepo rawRepo = gitHubService.fetchRepositoryMetadata(repo.getOwner(), repo.getName());
        if (rawRepo == null) {
            rawRepo = new GitHubService.RawGitHubRepo();
            rawRepo.setId(repo.getId());
            rawRepo.setName(repo.getName());
            rawRepo.setFullName(repo.getFullName());
            rawRepo.setDescription(repo.getDescription());
            rawRepo.setLanguage(repo.getPrimaryLanguage());
            rawRepo.setStargazersCount(repo.getStarsCount());
            rawRepo.setForksCount(repo.getForksCount());
        }

        AIRepositoryMatchResponse match = aiRepositoryMatchingService.evaluateCandidate(rawRepo, requirements);
        return ResponseEntity.ok(ApiResponse.ok(match));
    }

    // -------------------------------------------------------------------------
    // Saved AI Project Searches
    // -------------------------------------------------------------------------

    @GetMapping("/searches")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSavedSearches(
            @AuthenticationPrincipal FirebaseUserPrincipal principal
    ) {
        if (principal == null || principal.getUser() == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required to view saved searches"));
        }

        List<ProjectRequirement> list = projectRequirementRepository.findByUserOrderByCreatedAtDesc(principal.getUser());
        List<Map<String, Object>> response = new ArrayList<>();
        for (ProjectRequirement pr : list) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", pr.getId());
            map.put("title", pr.getTitle() != null ? pr.getTitle() : pr.getDescription());
            map.put("description", pr.getDescription());
            map.put("category", pr.getCategory());
            map.put("createdAt", pr.getCreatedAt());
            try {
                if (pr.getStructuredRequirementsJson() != null) {
                    map.put("structuredRequirements", objectMapper.readValue(pr.getStructuredRequirementsJson(), Object.class));
                }
                if (pr.getMatchesJson() != null) {
                    map.put("matches", objectMapper.readValue(pr.getMatchesJson(), Object.class));
                }
            } catch (Exception ignored) {}
            response.add(map);
        }

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/searches")
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveSearch(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @RequestBody Map<String, Object> body
    ) {
        if (principal == null || principal.getUser() == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required to save searches"));
        }

        String description = (String) body.get("description");
        String title = (String) body.get("title");
        String category = (String) body.getOrDefault("category", "other");
        Object structured = body.get("structuredRequirements");
        Object matches = body.get("matches");

        if (description == null || description.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Description is required"));
        }

        String structuredJson = null;
        String matchesJson = null;
        try {
            if (structured != null) {
                structuredJson = objectMapper.writeValueAsString(structured);
            }
            if (matches != null) {
                matchesJson = objectMapper.writeValueAsString(matches);
            }
        } catch (Exception ignored) {}

        ProjectRequirement pr = ProjectRequirement.builder()
                .user(principal.getUser())
                .title(title != null && !title.isBlank() ? title : (description.length() > 50 ? description.substring(0, 50) + "..." : description))
                .description(description)
                .category(category)
                .structuredRequirementsJson(structuredJson)
                .matchesJson(matchesJson)
                .build();

        ProjectRequirement saved = projectRequirementRepository.save(pr);

        return ResponseEntity.ok(ApiResponse.ok("Search saved successfully", Map.of(
                "id", saved.getId(),
                "title", saved.getTitle(),
                "description", saved.getDescription()
        )));
    }

    @DeleteMapping("/searches/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSavedSearch(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable Long id
    ) {
        if (principal == null || principal.getUser() == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }

        projectRequirementRepository.deleteByIdAndUser(id, principal.getUser());
        return ResponseEntity.ok(ApiResponse.ok("Saved search deleted", null));
    }
}
