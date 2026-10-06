package com.openinfra.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "analyses", indexes = {
    @Index(name = "idx_analysis_repo_id", columnList = "repository_id"),
    @Index(name = "idx_analysis_analyzed_at", columnList = "analyzed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User analyzedBy;

    @Column(name = "assessment_status", length = 50)
    private String assessmentStatus;

    @Column(name = "composite_score")
    private Double compositeScore;

    @Column(name = "continuity_score")
    private Double continuityScore;

    @Column(name = "continuity_status", length = 100)
    private String continuityStatus;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "ml_predicted_status", length = 100)
    private String mlPredictedStatus;

    @Column(name = "ml_algorithm", length = 150)
    private String mlAlgorithm;

    @Column(name = "popularity_score")
    private Double popularityScore;

    @Column(name = "popularity_tier", length = 100)
    private String popularityTier;

    @Column(name = "adoption_recommendation", length = 100)
    private String adoptionRecommendation;

    @Column(name = "adoption_verdict", columnDefinition = "TEXT")
    private String adoptionVerdict;

    @Column(name = "analysis_summary", columnDefinition = "TEXT")
    private String analysisSummary;

    @Column(name = "analysis_result", columnDefinition = "LONGTEXT")
    private String analysisResult;

    // Detailed JSON payloads for full fidelity with frontend domain models
    @Column(name = "key_observations_json", columnDefinition = "LONGTEXT")
    private String keyObservationsJson;

    @Column(name = "core_metrics_json", columnDefinition = "LONGTEXT")
    private String coreMetricsJson;

    @Column(name = "supporting_data_json", columnDefinition = "LONGTEXT")
    private String supportingDataJson;

    @Column(name = "popularity_metrics_json", columnDefinition = "LONGTEXT")
    private String popularityMetricsJson;

    @Column(name = "scoring_breakdown_json", columnDefinition = "LONGTEXT")
    private String scoringBreakdownJson;

    @Column(name = "ml_continuity_model_json", columnDefinition = "LONGTEXT")
    private String mlContinuityModelJson;

    @Column(name = "maintainer_distribution_json", columnDefinition = "LONGTEXT")
    private String maintainerDistributionJson;

    @Column(name = "release_continuity_json", columnDefinition = "LONGTEXT")
    private String releaseContinuityJson;

    @Column(name = "repository_activity_json", columnDefinition = "LONGTEXT")
    private String repositoryActivityJson;

    @Column(name = "governance_json", columnDefinition = "LONGTEXT")
    private String governanceJson;

    @Column(name = "sustainability_indicators_json", columnDefinition = "LONGTEXT")
    private String sustainabilityIndicatorsJson;

    @Column(name = "raw_analysis_json", columnDefinition = "LONGTEXT")
    private String rawAnalysisJson;

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.analyzedAt == null) {
            this.analyzedAt = LocalDateTime.now();
        }
        this.createdAt = LocalDateTime.now();
        if (this.analysisSummary == null && this.adoptionVerdict != null) {
            this.analysisSummary = this.adoptionVerdict;
        }
        if (this.analysisResult == null && this.rawAnalysisJson != null) {
            this.analysisResult = this.rawAnalysisJson;
        }
    }
}
