package com.openinfra.backend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openinfra.backend.entity.RepositoryAnalysis;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisResponse {

    private Long id;
    private Long repositoryId;
    private String repositoryOwner;
    private String repositoryName;
    private String repositoryFullName;
    private String assessmentStatus;

    private Double compositeScore;
    private Double continuityScore;
    private String continuityStatus;
    private Double confidenceScore;
    private String mlPredictedStatus;
    private String mlAlgorithm;
    private Double popularityScore;
    private String popularityTier;
    private String adoptionRecommendation;
    private String adoptionVerdict;

    // AI Repository Intelligence Fields (Google Gemini)
    private String aiSummary;
    private String aiArchitecturalAssessment;
    private String aiRiskAssessment;
    private String aiAdoptionVerdict;
    private String aiCommunitySentiment;
    private JsonNode aiRecommendations;
    private JsonNode aiInsights;
    private LocalDateTime aiGeneratedAt;

    private JsonNode rawAssessment;
    private LocalDateTime analyzedAt;
    private LocalDateTime createdAt;

    public static AnalysisResponse fromEntity(RepositoryAnalysis analysis, ObjectMapper objectMapper) {
        if (analysis == null) return null;

        JsonNode parsedRaw = null;
        if (analysis.getRawAnalysisJson() != null && !analysis.getRawAnalysisJson().isBlank()) {
            try {
                parsedRaw = objectMapper.readTree(analysis.getRawAnalysisJson());
            } catch (Exception ignored) {
            }
        }

        JsonNode parsedAiRecommendations = null;
        if (analysis.getAiRecommendationsJson() != null && !analysis.getAiRecommendationsJson().isBlank()) {
            try {
                parsedAiRecommendations = objectMapper.readTree(analysis.getAiRecommendationsJson());
            } catch (Exception ignored) {
            }
        }

        JsonNode parsedAiInsights = null;
        if (analysis.getAiInsightsJson() != null && !analysis.getAiInsightsJson().isBlank()) {
            try {
                parsedAiInsights = objectMapper.readTree(analysis.getAiInsightsJson());
            } catch (Exception ignored) {
            }
        }

        return AnalysisResponse.builder()
                .id(analysis.getId())
                .repositoryId(analysis.getRepository() != null ? analysis.getRepository().getId() : null)
                .repositoryOwner(analysis.getRepository() != null ? analysis.getRepository().getOwner() : null)
                .repositoryName(analysis.getRepository() != null ? analysis.getRepository().getName() : null)
                .repositoryFullName(analysis.getRepository() != null ? analysis.getRepository().getFullName() : null)
                .assessmentStatus(analysis.getAssessmentStatus())
                .compositeScore(analysis.getCompositeScore())
                .continuityScore(analysis.getContinuityScore())
                .continuityStatus(analysis.getContinuityStatus())
                .confidenceScore(analysis.getConfidenceScore())
                .mlPredictedStatus(analysis.getMlPredictedStatus())
                .mlAlgorithm(analysis.getMlAlgorithm())
                .popularityScore(analysis.getPopularityScore())
                .popularityTier(analysis.getPopularityTier())
                .adoptionRecommendation(analysis.getAdoptionRecommendation())
                .adoptionVerdict(analysis.getAdoptionVerdict())
                .aiSummary(analysis.getAiSummary())
                .aiArchitecturalAssessment(analysis.getAiArchitecturalAssessment())
                .aiRiskAssessment(analysis.getAiRiskAssessment())
                .aiAdoptionVerdict(analysis.getAiAdoptionVerdict())
                .aiCommunitySentiment(analysis.getAiCommunitySentiment())
                .aiRecommendations(parsedAiRecommendations)
                .aiInsights(parsedAiInsights)
                .aiGeneratedAt(analysis.getAiGeneratedAt())
                .rawAssessment(parsedRaw)
                .analyzedAt(analysis.getAnalyzedAt())
                .createdAt(analysis.getCreatedAt())
                .build();
    }
}
