package com.openinfra.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openinfra.backend.dto.AnalysisResponse;
import com.openinfra.backend.dto.AnalysisSaveRequest;
import com.openinfra.backend.entity.Repository;
import com.openinfra.backend.entity.RepositoryAnalysis;
import com.openinfra.backend.entity.User;
import com.openinfra.backend.exception.ResourceNotFoundException;
import com.openinfra.backend.repository.RepositoryAnalysisRepository;
import com.openinfra.backend.repository.RepositoryRepository;
import com.openinfra.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
public class AnalysisService {

    private final RepositoryAnalysisRepository analysisRepository;
    private final RepositoryRepository repositoryRepository;
    private final RepositoryService repositoryService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AnalysisService(RepositoryAnalysisRepository analysisRepository,
                           RepositoryRepository repositoryRepository,
                           RepositoryService repositoryService,
                           UserRepository userRepository,
                           ObjectMapper objectMapper) {
        this.analysisRepository = analysisRepository;
        this.repositoryRepository = repositoryRepository;
        this.repositoryService = repositoryService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AnalysisResponse saveAnalysis(AnalysisSaveRequest request, String firebaseUid) {
        log.info("Saving analysis for repository {}/{}", request.getOwner(), request.getName());

        Repository repo = repositoryService.findOrCreateRepository(
                request.getOwner(),
                request.getName(),
                request.getUrl(),
                request.getDescription(),
                request.getLanguage()
        );

        User user = null;
        if (firebaseUid != null && !firebaseUid.isBlank() && !firebaseUid.equals("guest_workspace")) {
            user = userRepository.findByFirebaseUid(firebaseUid).orElse(null);
        }

        RepositoryAnalysis analysis = RepositoryAnalysis.builder()
                .repository(repo)
                .analyzedBy(user)
                .assessmentStatus(request.getStatus() != null ? request.getStatus() : "available")
                .compositeScore(request.getCompositeScore())
                .continuityScore(request.getContinuityScore())
                .continuityStatus(request.getContinuityStatus())
                .confidenceScore(request.getConfidenceScore())
                .mlPredictedStatus(request.getMlPredictedStatus())
                .mlAlgorithm(request.getMlAlgorithm())
                .popularityScore(request.getPopularityScore())
                .popularityTier(request.getPopularityTier())
                .adoptionRecommendation(request.getAdoptionRecommendation())
                .adoptionVerdict(request.getAdoptionVerdict())
                .analyzedAt(LocalDateTime.now())
                .build();

        // Serialize rich JSON fields safely
        try {
            if (request.getKeyObservations() != null) {
                analysis.setKeyObservationsJson(objectMapper.writeValueAsString(request.getKeyObservations()));
            }
            if (request.getCoreMetrics() != null) {
                analysis.setCoreMetricsJson(objectMapper.writeValueAsString(request.getCoreMetrics()));
            }
            if (request.getSupportingData() != null) {
                analysis.setSupportingDataJson(objectMapper.writeValueAsString(request.getSupportingData()));
            }
            if (request.getPopularityMetrics() != null) {
                analysis.setPopularityMetricsJson(objectMapper.writeValueAsString(request.getPopularityMetrics()));
            }
            if (request.getScoringBreakdown() != null) {
                analysis.setScoringBreakdownJson(objectMapper.writeValueAsString(request.getScoringBreakdown()));
            }
            if (request.getMlContinuityModel() != null) {
                analysis.setMlContinuityModelJson(objectMapper.writeValueAsString(request.getMlContinuityModel()));
            }
            if (request.getMaintainerDistribution() != null) {
                analysis.setMaintainerDistributionJson(objectMapper.writeValueAsString(request.getMaintainerDistribution()));
            }
            if (request.getReleaseContinuity() != null) {
                analysis.setReleaseContinuityJson(objectMapper.writeValueAsString(request.getReleaseContinuity()));
            }
            if (request.getRepositoryActivity() != null) {
                analysis.setRepositoryActivityJson(objectMapper.writeValueAsString(request.getRepositoryActivity()));
            }
            if (request.getGovernance() != null) {
                analysis.setGovernanceJson(objectMapper.writeValueAsString(request.getGovernance()));
            }
            if (request.getSustainabilityIndicators() != null) {
                analysis.setSustainabilityIndicatorsJson(objectMapper.writeValueAsString(request.getSustainabilityIndicators()));
            }
            if (request.getRawAssessment() != null) {
                analysis.setRawAnalysisJson(objectMapper.writeValueAsString(request.getRawAssessment()));
            }
        } catch (Exception e) {
            log.warn("Failed to serialize one or more analysis JSON fields: {}", e.getMessage());
        }

        RepositoryAnalysis saved = analysisRepository.save(analysis);
        return AnalysisResponse.fromEntity(saved, objectMapper);
    }

    @Transactional(readOnly = true)
    public AnalysisResponse getLatestAnalysis(String owner, String name) {
        RepositoryAnalysis analysis = analysisRepository.findLatestByOwnerAndName(owner.trim(), name.trim())
                .orElseThrow(() -> new ResourceNotFoundException("No analysis found for " + owner + "/" + name));
        return AnalysisResponse.fromEntity(analysis, objectMapper);
    }

    @Transactional(readOnly = true)
    public AnalysisResponse getAnalysisById(Long id) {
        RepositoryAnalysis analysis = analysisRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found with ID: " + id));
        return AnalysisResponse.fromEntity(analysis, objectMapper);
    }

    @Transactional(readOnly = true)
    public Page<AnalysisResponse> getAnalysesByRepository(Long repositoryId, Pageable pageable) {
        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found with ID: " + repositoryId));
        return analysisRepository.findByRepositoryOrderByAnalyzedAtDesc(repo, pageable)
                .map(a -> AnalysisResponse.fromEntity(a, objectMapper));
    }
}
