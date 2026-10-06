package com.openinfra.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openinfra.backend.dto.AnalysisSaveRequest;
import com.openinfra.backend.dto.SearchHistoryRequest;
import com.openinfra.backend.dto.SearchHistoryResponse;
import com.openinfra.backend.entity.Repository;
import com.openinfra.backend.entity.RepositoryAnalysis;
import com.openinfra.backend.entity.SearchHistory;
import com.openinfra.backend.entity.User;
import com.openinfra.backend.exception.ResourceNotFoundException;
import com.openinfra.backend.repository.RepositoryAnalysisRepository;
import com.openinfra.backend.repository.SearchHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SearchHistoryService {

    private final SearchHistoryRepository searchHistoryRepository;
    private final UserService userService;
    private final RepositoryService repositoryService;
    private final RepositoryAnalysisRepository analysisRepository;
    private final AnalysisService analysisService;
    private final ObjectMapper objectMapper;

    public SearchHistoryService(SearchHistoryRepository searchHistoryRepository,
                                UserService userService,
                                RepositoryService repositoryService,
                                RepositoryAnalysisRepository analysisRepository,
                                AnalysisService analysisService,
                                ObjectMapper objectMapper) {
        this.searchHistoryRepository = searchHistoryRepository;
        this.userService = userService;
        this.repositoryService = repositoryService;
        this.analysisRepository = analysisRepository;
        this.analysisService = analysisService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SearchHistoryResponse recordSearch(String firebaseUid, SearchHistoryRequest request) {
        log.info("Recording search history for user {} on repo {}/{}",
                firebaseUid, request.getOwner(), request.getRepositoryName());

        User user = userService.getOrCreateEntity(firebaseUid, null, null);

        Repository repository = repositoryService.findOrCreateRepository(
                request.getOwner(),
                request.getRepositoryName(),
                request.getRepositoryUrl(),
                request.getDescription(),
                request.getLanguage()
        );

        // If assessment data was provided with the search, save an analysis snapshot
        RepositoryAnalysis analysis = null;
        if (request.getAssessmentData() != null && !request.getAssessmentData().isNull()) {
            try {
                AnalysisSaveRequest saveReq = AnalysisSaveRequest.builder()
                        .owner(request.getOwner())
                        .name(request.getRepositoryName())
                        .url(request.getRepositoryUrl())
                        .description(request.getDescription())
                        .language(request.getLanguage())
                        .status(request.getStatus() != null ? request.getStatus() : "available")
                        .rawAssessment(request.getAssessmentData())
                        .build();

                if (request.getAssessmentData().has("maintenanceContinuity")) {
                    var mc = request.getAssessmentData().get("maintenanceContinuity");
                    if (mc.has("score")) saveReq.setContinuityScore(mc.get("score").asDouble());
                    if (mc.has("status")) saveReq.setContinuityStatus(mc.get("status").asText());
                }
                if (request.getAssessmentData().has("scoringBreakdown") &&
                        request.getAssessmentData().get("scoringBreakdown").has("compositeScore")) {
                    saveReq.setCompositeScore(request.getAssessmentData().get("scoringBreakdown").get("compositeScore").asDouble());
                }

                analysisService.saveAnalysis(saveReq, firebaseUid);
            } catch (Exception e) {
                log.warn("Failed to auto-save analysis during search record: {}", e.getMessage());
            }
        }

        // Fetch latest analysis snapshot for linking
        analysis = analysisRepository.findLatestByOwnerAndName(request.getOwner(), request.getRepositoryName()).orElse(null);

        LocalDateTime now = LocalDateTime.now();
        SearchHistory searchHistory = searchHistoryRepository.findByUserAndRepository(user, repository)
                .orElseGet(() -> SearchHistory.builder()
                        .user(user)
                        .repository(repository)
                        .searchQuery(request.getRepositoryUrl() != null ? request.getRepositoryUrl() : request.getOwner() + "/" + request.getRepositoryName())
                        .searchedAt(now)
                        .viewCount(0)
                        .build());

        searchHistory.setLastViewedAt(now);
        searchHistory.setViewCount((searchHistory.getViewCount() != null ? searchHistory.getViewCount() : 0) + 1);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            searchHistory.setStatusSnapshot(request.getStatus());
        }
        if (analysis != null) {
            searchHistory.setLatestAnalysis(analysis);
        }

        SearchHistory saved = searchHistoryRepository.save(searchHistory);
        return SearchHistoryResponse.fromEntity(saved, objectMapper);
    }

    @Transactional(readOnly = true)
    public List<SearchHistoryResponse> getUserHistory(String firebaseUid) {
        return searchHistoryRepository.findByUser_FirebaseUidOrderByLastViewedAtDesc(firebaseUid)
                .stream()
                .map(item -> SearchHistoryResponse.fromEntity(item, objectMapper))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteHistoryItem(String firebaseUid, Long historyId) {
        User user = userService.getOrCreateEntity(firebaseUid, null, null);
        int deleted = searchHistoryRepository.deleteByUserAndId(user, historyId);
        if (deleted == 0) {
            throw new ResourceNotFoundException("History item not found or does not belong to user");
        }
    }

    @Transactional
    public void clearUserHistory(String firebaseUid) {
        User user = userService.getOrCreateEntity(firebaseUid, null, null);
        searchHistoryRepository.deleteByUser(user);
    }
}
