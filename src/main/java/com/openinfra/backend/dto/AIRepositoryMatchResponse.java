package com.openinfra.backend.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIRepositoryMatchResponse {

    private Long repositoryId;
    private String owner;
    private String name;
    private String fullName;
    private String htmlUrl;
    private String description;
    private String primaryLanguage;
    private Integer starsCount;
    private Integer forksCount;
    private Integer daysSinceLastPush;

    // AI & Quantitative Scoring
    private Integer matchScore;            // 0 - 100
    private Integer domainRelevanceScore;   // 0 - 100 (Domain relevance tailored to user's requested domain, e.g. Cloud, ML, Frontend, Backend)
    private Integer cloudRelevanceScore;   // 0 - 100 (Kept for backward compatibility)
    private String relevanceLabel;         // e.g. "Cloud Relevance", "AI/ML Relevance", "Frontend Relevance", "Backend Relevance", "Domain Relevance"
    private Double sustainabilityScore;    // 0 - 100 (from deterministic engine)
    private Double finalRankingScore;      // Deterministic combined composite
    private String category;

    @Builder.Default
    private List<RequirementItem> requirements = new ArrayList<>();

    @Builder.Default
    private List<String> strengths = new ArrayList<>();

    @Builder.Default
    private List<String> weaknesses = new ArrayList<>();

    @Builder.Default
    private List<String> recommendations = new ArrayList<>();

    private String summary;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RequirementItem {
        private String requirement;
        private String status; // MATCHED, PARTIALLY_MATCHED, MISSING, UNKNOWN
        private Double confidence;
        private String evidence;
    }
}
