package com.openinfra.backend.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIRequirementResponse {

    private String projectType;
    private String category;

    @Builder.Default
    private List<String> cloudProviders = new ArrayList<>();

    @Builder.Default
    private List<String> requiredTechnologies = new ArrayList<>();

    @Builder.Default
    private List<String> preferredTechnologies = new ArrayList<>();

    @Builder.Default
    private List<String> requiredFeatures = new ArrayList<>();

    @Builder.Default
    private List<String> monitoringRequirements = new ArrayList<>();

    @Builder.Default
    private List<String> networkingRequirements = new ArrayList<>();

    @Builder.Default
    private List<String> securityRequirements = new ArrayList<>();

    @Builder.Default
    private List<String> databaseRequirements = new ArrayList<>();

    @Builder.Default
    private List<String> deploymentRequirements = new ArrayList<>();

    @Builder.Default
    private List<String> maintenancePreferences = new ArrayList<>();

    private String complexity;

    @Builder.Default
    private List<String> keywords = new ArrayList<>();

    private String rawQuery;
    private String summaryText;
}
