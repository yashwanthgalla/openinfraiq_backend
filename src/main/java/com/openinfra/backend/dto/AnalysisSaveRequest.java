package com.openinfra.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisSaveRequest {

    @NotBlank(message = "Repository owner is required")
    private String owner;

    @NotBlank(message = "Repository name is required")
    private String name;

    private String url;
    private String description;
    private String language;
    private String status;

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
    private List<String> keyObservations;

    // Rich JSON objects from frontend analyzer
    private JsonNode coreMetrics;
    private JsonNode supportingData;
    private JsonNode popularityMetrics;
    private JsonNode scoringBreakdown;
    private JsonNode mlContinuityModel;
    private JsonNode maintainerDistribution;
    private JsonNode releaseContinuity;
    private JsonNode repositoryActivity;
    private JsonNode governance;
    private JsonNode sustainabilityIndicators;
    private JsonNode rawAssessment;
}
