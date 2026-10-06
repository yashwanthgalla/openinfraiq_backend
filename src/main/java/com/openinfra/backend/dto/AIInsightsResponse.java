package com.openinfra.backend.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIInsightsResponse {

    private String executiveSummary;
    private String architecturalAssessment;
    private String riskAnalysis;
    private String adoptionVerdict;

    @Builder.Default
    private List<String> recommendations = new ArrayList<>();

    private String communitySentiment;

    @Builder.Default
    private List<String> keyRisks = new ArrayList<>();

    @Builder.Default
    private List<String> strengths = new ArrayList<>();

    private Double confidenceScore;
    private String modelName;
    private String generatedAt;
    private Boolean isAiAvailable;
}
