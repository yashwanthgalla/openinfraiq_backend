package com.openinfra.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openinfra.backend.dto.RepositoryResponse;
import com.openinfra.backend.entity.Repository;
import com.openinfra.backend.entity.RepositoryAnalysis;
import com.openinfra.backend.entity.SearchHistory;
import com.openinfra.backend.entity.User;
import com.openinfra.backend.exception.ResourceNotFoundException;
import com.openinfra.backend.repository.RepositoryAnalysisRepository;
import com.openinfra.backend.repository.RepositoryRepository;
import com.openinfra.backend.repository.SearchHistoryRepository;
import com.openinfra.backend.service.GitHubService.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class RepositoryService {

    private final RepositoryRepository repositoryRepository;
    private final RepositoryAnalysisRepository analysisRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final GitHubService gitHubService;
    private final AnalysisEngineService analysisEngineService;
    private final ObjectMapper objectMapper;

    public RepositoryService(RepositoryRepository repositoryRepository,
                             RepositoryAnalysisRepository analysisRepository,
                             SearchHistoryRepository searchHistoryRepository,
                             GitHubService gitHubService,
                             AnalysisEngineService analysisEngineService,
                             ObjectMapper objectMapper) {
        this.repositoryRepository = repositoryRepository;
        this.analysisRepository = analysisRepository;
        this.searchHistoryRepository = searchHistoryRepository;
        this.gitHubService = gitHubService;
        this.analysisEngineService = analysisEngineService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> analyzeAndPersist(User user, String owner, String name) {
        String cleanOwner = owner.trim();
        String cleanName = name.trim();
        String fullName = cleanOwner.toLowerCase() + "/" + cleanName.toLowerCase();
        log.info("Executing backend analysis for user {} on repository {}", user.getEmail(), fullName);

        // 1. Fetch live repository details from GitHub
        RawGitHubRepo metadata = gitHubService.fetchRepositoryMetadata(cleanOwner, cleanName);
        List<RawGitHubContributor> contributors = gitHubService.fetchContributors(cleanOwner, cleanName);
        List<RawGitHubRelease> releases = gitHubService.fetchReleases(cleanOwner, cleanName);
        List<RawGitHubCommit> commits = gitHubService.fetchRecentCommits(cleanOwner, cleanName);
        List<RawGitHubPull> pulls = gitHubService.fetchRecentPulls(cleanOwner, cleanName);
        List<RawGitHubIssue> issues = gitHubService.fetchRecentIssues(cleanOwner, cleanName);

        // 2. Perform authentic multi-dimensional sustainability calculation
        Map<String, Object> analysisResult = analysisEngineService.executeAnalysis(
                metadata, contributors, releases, commits, pulls, issues
        );

        // 3. Find or create user-scoped Repository record
        Repository repository = repositoryRepository.findByUserAndFullNameIgnoreCase(user, fullName)
                .orElseGet(() -> Repository.builder()
                        .user(user)
                        .owner(cleanOwner)
                        .name(cleanName)
                        .fullName(fullName)
                        .build());

        populateRepositoryDetails(repository, metadata, fullName);

        Repository savedRepository = repositoryRepository.save(repository);

        // 4. Create and persist new Analysis record automatically
        String rawJson = null;
        try {
            rawJson = objectMapper.writeValueAsString(analysisResult);
        } catch (Exception e) {
            log.warn("Failed to serialize raw analysis json: {}", e.getMessage());
        }

        Double compositeScore = analysisResult.containsKey("scoringBreakdown")
                ? ((Number) ((Map<?, ?>) analysisResult.get("scoringBreakdown")).get("compositeScore")).doubleValue()
                : 70.0;

        String continuityStatus = analysisResult.containsKey("maintenanceContinuity")
                ? (String) ((Map<?, ?>) analysisResult.get("maintenanceContinuity")).get("status")
                : "Moderate Continuity";

        String recommendation = analysisResult.containsKey("adoptionAssessment")
                ? (String) ((Map<?, ?>) analysisResult.get("adoptionAssessment")).get("recommendation")
                : "Adoption with Precaution";

        String verdict = analysisResult.containsKey("adoptionAssessment")
                ? (String) ((Map<?, ?>) analysisResult.get("adoptionAssessment")).get("verdict")
                : "";

        RepositoryAnalysis analysis = RepositoryAnalysis.builder()
                .repository(savedRepository)
                .analyzedBy(user)
                .assessmentStatus("available")
                .compositeScore(compositeScore)
                .continuityScore(compositeScore)
                .continuityStatus(continuityStatus)
                .adoptionRecommendation(recommendation)
                .adoptionVerdict(verdict)
                .analysisSummary(verdict)
                .analysisResult(rawJson)
                .rawAnalysisJson(rawJson)
                .analyzedAt(LocalDateTime.now())
                .build();

        RepositoryAnalysis savedAnalysis = analysisRepository.save(analysis);

        // 5. Automatically record in SearchHistory
        LocalDateTime now = LocalDateTime.now();
        SearchHistory searchHistory = searchHistoryRepository.findByUserAndRepository(user, savedRepository)
                .orElseGet(() -> SearchHistory.builder()
                        .user(user)
                        .repository(savedRepository)
                        .searchQuery(savedRepository.getUrl())
                        .searchedAt(now)
                        .viewCount(0)
                        .build());

        searchHistory.setLastViewedAt(now);
        searchHistory.setViewCount((searchHistory.getViewCount() != null ? searchHistory.getViewCount() : 0) + 1);
        searchHistory.setStatusSnapshot("available");
        searchHistory.setLatestAnalysis(savedAnalysis);
        searchHistoryRepository.save(searchHistory);

        // Inject repository internal ID into result for frontend referencing
        analysisResult.put("internalRepositoryId", savedRepository.getId());
        analysisResult.put("analysisId", savedAnalysis.getId());

        log.info("Analysis completed and saved for {} with composite score {}", fullName, compositeScore);
        return analysisResult;
    }

    @Transactional(readOnly = true)
    public Page<RepositoryResponse> getUserRepositories(User user, Pageable pageable) {
        return repositoryRepository.findByUserOrderByUpdatedAtDesc(user, pageable)
                .map(RepositoryResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public RepositoryResponse getRepositoryById(User user, Long id) {
        Repository repo = repositoryRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found with ID: " + id));
        return RepositoryResponse.fromEntity(repo);
    }

    @Transactional
    public void deleteRepositoryById(User user, Long id) {
        int deleted = repositoryRepository.deleteByUserAndId(user, id);
        if (deleted == 0) {
            throw new ResourceNotFoundException("Repository not found or does not belong to user");
        }
    }

    @Transactional(readOnly = true)
    public Page<RepositoryAnalysis> getRepositoryAnalyses(User user, Long repoId, Pageable pageable) {
        // Enforce user ownership of repository
        repositoryRepository.findByUserAndId(user, repoId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found with ID: " + repoId));

        return analysisRepository.findByRepository_UserAndRepository_IdOrderByAnalyzedAtDesc(user, repoId, pageable);
    }

    @Transactional
    public Repository findOrCreateRepository(String owner, String name, String url, String description, String language) {
        String cleanOwner = owner.trim();
        String cleanName = name.trim();
        String fullName = cleanOwner.toLowerCase() + "/" + cleanName.toLowerCase();

        Repository repo = repositoryRepository.findFirstByFullNameIgnoreCaseOrderByUpdatedAtDesc(fullName)
                .orElseGet(() -> Repository.builder()
                        .owner(cleanOwner)
                        .name(cleanName)
                        .fullName(fullName)
                        .url(url != null ? url : "https://github.com/" + fullName)
                        .htmlUrl(url != null ? url : "https://github.com/" + fullName)
                        .description(description)
                        .primaryLanguage(language)
                        .build());

        // Always ensure complete metadata is saved (stars, forks, open issues, watchers, license, clone URL, etc.)
        if (repo.getStarsCount() == null || repo.getForksCount() == null || repo.getCloneUrl() == null) {
            try {
                RawGitHubRepo metadata = gitHubService.fetchRepositoryMetadata(cleanOwner, cleanName);
                if (metadata != null) {
                    populateRepositoryDetails(repo, metadata, fullName);
                }
            } catch (Exception e) {
                log.warn("Could not enrich repository metadata from GitHub for {}: {}", fullName, e.getMessage());
            }
        }

        return repositoryRepository.save(repo);
    }

    public void populateRepositoryDetails(Repository repository, RawGitHubRepo metadata, String fullName) {
        if (metadata == null) return;
        if (metadata.getId() != null) repository.setGithubRepositoryId(metadata.getId());
        repository.setUrl(metadata.getHtmlUrl() != null ? metadata.getHtmlUrl() : "https://github.com/" + fullName);
        repository.setHtmlUrl(repository.getUrl());
        if (metadata.getCloneUrl() != null) repository.setCloneUrl(metadata.getCloneUrl());
        if (metadata.getDescription() != null) repository.setDescription(metadata.getDescription());
        if (metadata.getLanguage() != null) repository.setPrimaryLanguage(metadata.getLanguage());
        if (metadata.getDefaultBranch() != null) repository.setDefaultBranch(metadata.getDefaultBranch());
        repository.setStarsCount(metadata.getStargazersCount() != null ? metadata.getStargazersCount() : 0);
        repository.setForksCount(metadata.getForksCount() != null ? metadata.getForksCount() : 0);
        repository.setOpenIssuesCount(metadata.getOpenIssuesCount() != null ? metadata.getOpenIssuesCount() : 0);
        repository.setWatchersCount(metadata.getWatchersCount() != null ? metadata.getWatchersCount() : (metadata.getSubscribersCount() != null ? metadata.getSubscribersCount() : 0));
        repository.setIsPrivate(Boolean.TRUE.equals(metadata.getIsPrivate()));
        repository.setIsArchived(Boolean.TRUE.equals(metadata.getArchived()));
        if (metadata.getLicense() != null) {
            repository.setLicenseName(metadata.getLicense().getName());
            repository.setLicenseSpdxId(metadata.getLicense().getSpdxId());
        }
        if (metadata.getPushedAt() != null) {
            try {
                repository.setLastActivityAt(LocalDateTime.ofInstant(Instant.parse(metadata.getPushedAt()), ZoneOffset.UTC));
            } catch (Exception ignored) {}
        }
    }
}
