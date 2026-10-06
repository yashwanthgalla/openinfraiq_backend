package com.openinfra.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openinfra.backend.dto.AIInsightsResponse;
import com.openinfra.backend.entity.Repository;
import com.openinfra.backend.entity.RepositoryAnalysis;
import com.openinfra.backend.repository.RepositoryAnalysisRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class AIAnalysisService {

    private final GeminiService geminiService;
    private final GitHubService gitHubService;
    private final RepositoryAnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper;

    public AIAnalysisService(GeminiService geminiService,
                             GitHubService gitHubService,
                             RepositoryAnalysisRepository analysisRepository,
                             ObjectMapper objectMapper) {
        this.geminiService = geminiService;
        this.gitHubService = gitHubService;
        this.analysisRepository = analysisRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates qualitative AI insights for a repository using Google Gemini.
     * Guaranteed to never throw fatal exceptions: if AI fails, returns graceful degraded insights.
     */
    public AIInsightsResponse generateRepositoryInsights(Repository repo,
                                                         Map<String, Object> quantitativeAnalysis,
                                                         List<GitHubService.RawGitHubIssue> recentIssues,
                                                         List<GitHubService.RawGitHubPull> recentPulls) {
        if (!geminiService.isConfigured()) {
            log.info("Gemini API key is not configured; skipping AI repository intelligence generation for {}", repo.getFullName());
            return createDegradedResponse("Gemini API key not configured on server.");
        }

        try {
            // 1. Fetch README snippet for architectural context (max 3KB)
            String readmeSnippet = "";
            try {
                readmeSnippet = gitHubService.fetchReadme(repo.getOwner(), repo.getName());
                if (readmeSnippet.length() > 3000) {
                    readmeSnippet = readmeSnippet.substring(0, 3000) + "... [truncated]";
                }
            } catch (Exception e) {
                log.debug("Readme fetch omitted for AI prompt: {}", e.getMessage());
            }

            // 2. Prepare structured context payload
            Map<String, Object> promptPayload = new LinkedHashMap<>();
            Map<String, Object> repoData = new LinkedHashMap<>();
            repoData.put("fullName", repo.getFullName());
            repoData.put("description", repo.getDescription() != null ? repo.getDescription() : "None provided");
            repoData.put("primaryLanguage", repo.getPrimaryLanguage() != null ? repo.getPrimaryLanguage() : "Unknown");
            repoData.put("stars", repo.getStarsCount() != null ? repo.getStarsCount() : 0);
            repoData.put("forks", repo.getForksCount() != null ? repo.getForksCount() : 0);
            repoData.put("openIssues", repo.getOpenIssuesCount() != null ? repo.getOpenIssuesCount() : 0);
            repoData.put("license", repo.getLicenseName() != null ? repo.getLicenseName() : "No recognized license");
            repoData.put("isArchived", Boolean.TRUE.equals(repo.getIsArchived()));
            promptPayload.put("repository", repoData);

            // Extract quantitative metrics from AnalysisEngineService output
            if (quantitativeAnalysis != null) {
                Map<String, Object> metricsSummary = new LinkedHashMap<>();
                metricsSummary.put("compositeScore", quantitativeAnalysis.getOrDefault("compositeScore",
                        quantitativeAnalysis.getOrDefault("scoringBreakdown", Map.of())));
                metricsSummary.put("coreMetrics", quantitativeAnalysis.get("coreMetrics"));
                metricsSummary.put("maintenanceContinuity", quantitativeAnalysis.get("maintenanceContinuity"));
                metricsSummary.put("adoptionAssessment", quantitativeAnalysis.get("adoptionAssessment"));
                metricsSummary.put("maintainerDistribution", quantitativeAnalysis.get("maintainerDistribution"));
                metricsSummary.put("releaseContinuity", quantitativeAnalysis.get("releaseContinuity"));
                promptPayload.put("quantitativeSustainabilityAnalysis", metricsSummary);
            }

            // Extract Issue / PR titles for community sentiment
            List<String> issueTitles = new ArrayList<>();
            if (recentIssues != null) {
                for (GitHubService.RawGitHubIssue issue : recentIssues) {
                    if (issue.getTitle() != null) issueTitles.add(issue.getTitle());
                    if (issueTitles.size() >= 8) break;
                }
            }
            promptPayload.put("recentIssueSamples", issueTitles);

            List<String> prTitles = new ArrayList<>();
            if (recentPulls != null) {
                for (GitHubService.RawGitHubPull pull : recentPulls) {
                    if (pull.getTitle() != null) prTitles.add(pull.getTitle());
                    if (prTitles.size() >= 8) break;
                }
            }
            promptPayload.put("recentPullRequestSamples", prTitles);

            if (!readmeSnippet.isBlank()) {
                promptPayload.put("readmeSnippet", geminiService.redactSecrets(readmeSnippet));
            }

            String contextJson = objectMapper.writeValueAsString(promptPayload);

            // 3. System Instruction
            String systemInstruction = """
                You are a Senior Principal Software Architect & Open-Source Sustainability Auditor for OpenInfraIQ.
                Your task is to provide expert, qualitative technical intelligence on ANY open-source software repository (including Cloud Infrastructure, DevOps, Backend APIs, Frontend applications, Full-Stack projects, Microservices, Machine Learning & AI, Data Engineering, Automation, Systems, and Developer Tools) tailored precisely to the repository's actual domain, architecture, and technology stack.

                CRITICAL OPERATIONAL RULES:
                1. DO NOT HALLUCINATE: Only use facts verifiable in the supplied repository metadata, languages, and README.
                2. ADAPT TO DOMAIN & NEEDS: Tailor your architectural analysis to the actual domain of the project (e.g. if it is a React app, analyze UI component architecture and state management; if it is an ML model, analyze model pipeline, datasets, and framework; if it is a backend service, analyze API design and persistence; if it is a cloud/DevOps project, analyze IaC and container orchestration).
                3. Do NOT invent technologies. If a project does not use AWS, Terraform, or Kubernetes, do NOT claim it does.
                4. DO NOT recalculate or modify the mathematical sustainability score (0-100) provided in the quantitative analysis. The score is authoritative.
                5. For any missing technical detail, explicitly state 'UNKNOWN'.
                6. Distinguish FACT from INFERENCE in your assessment.

                Return strictly a JSON object with this exact schema:
                {
                  "executiveSummary": "Concise 2-3 sentence executive review of what this project does and its engineering maturity.",
                  "architecturalAssessment": "In-depth interpretation of the technical architecture, design patterns, and domain posture (e.g. cloud/IaC for infrastructure, component hierarchy & state for frontend, data/model pipeline for ML, service & data tier for backend).",
                  "riskAnalysis": "Qualitative diagnosis of project maintenance vulnerabilities based on the 9 sustainability metrics (e.g. bus factor, release cadence, push recency).",
                  "adoptionVerdict": "Recommended for Production | Recommended with Precaution | Elevated Maintenance Risk",
                  "recommendations": ["Recommendation 1", "Recommendation 2", "Recommendation 3"],
                  "communitySentiment": "Interpretation of recent issue triage, pull request responsiveness, and developer collaboration tone.",
                  "keyRisks": ["Risk point 1", "Risk point 2"],
                  "strengths": ["Architectural strength 1", "Sustainability strength 2"]
                }
                """;

            String userPrompt = "Analyze the following repository evidence and output the structured JSON analysis:\n\n" + contextJson;

            // 4. Query Gemini
            AIInsightsResponse response = geminiService.generateStructuredContent(
                    systemInstruction,
                    userPrompt,
                    AIInsightsResponse.class
            );

            if (response != null) {
                response.setModelName(geminiService.getModelName());
                response.setGeneratedAt(LocalDateTime.now().toString());
                response.setIsAiAvailable(true);
                return response;
            }

            return createDegradedResponse("Model produced empty response.");

        } catch (Exception e) {
            log.warn("AI repository intelligence generation encountered an error: {}", e.getMessage());
            return createDegradedResponse("AI insights temporarily unavailable: " + e.getMessage());
        }
    }

    /**
     * Attaches and saves AI insights to an existing RepositoryAnalysis entity.
     */
    @Transactional
    public void attachAiInsightsToAnalysis(RepositoryAnalysis analysis, AIInsightsResponse insights) {
        if (analysis == null || insights == null || !Boolean.TRUE.equals(insights.getIsAiAvailable())) {
            return;
        }

        try {
            analysis.setAiSummary(insights.getExecutiveSummary());
            analysis.setAiArchitecturalAssessment(insights.getArchitecturalAssessment());
            analysis.setAiRiskAssessment(insights.getRiskAnalysis());
            analysis.setAiAdoptionVerdict(insights.getAdoptionVerdict());
            analysis.setAiCommunitySentiment(insights.getCommunitySentiment());
            analysis.setAiGeneratedAt(LocalDateTime.now());

            if (insights.getRecommendations() != null) {
                analysis.setAiRecommendationsJson(objectMapper.writeValueAsString(insights.getRecommendations()));
            }
            analysis.setAiInsightsJson(objectMapper.writeValueAsString(insights));

            analysisRepository.save(analysis);
            log.info("Attached Gemini AI insights to analysis ID {}", analysis.getId());
        } catch (Exception e) {
            log.warn("Failed to serialize or attach AI insights to analysis {}: {}", analysis.getId(), e.getMessage());
        }
    }

    private AIInsightsResponse createDegradedResponse(String reason) {
        return AIInsightsResponse.builder()
                .isAiAvailable(false)
                .executiveSummary("Standard deterministic repository analysis complete. " + reason)
                .architecturalAssessment("Automated architectural assessment could not be generated at this time.")
                .riskAnalysis("Refer to the 9 Core Sustainability Metrics for quantitative risk posture.")
                .adoptionVerdict("Refer to Standard Assessment")
                .recommendations(List.of("Review the deterministic 9 metrics for maintainer and release indicators."))
                .communitySentiment("Data triage completed via quantitative metrics.")
                .keyRisks(Collections.emptyList())
                .strengths(Collections.emptyList())
                .modelName("none")
                .generatedAt(LocalDateTime.now().toString())
                .build();
    }
}
