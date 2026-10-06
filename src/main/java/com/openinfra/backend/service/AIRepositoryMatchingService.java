package com.openinfra.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openinfra.backend.dto.AIRequirementResponse;
import com.openinfra.backend.dto.AIRepositoryMatchResponse;
import com.openinfra.backend.service.GitHubService.RawGitHubRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AIRepositoryMatchingService {

    private final GeminiService geminiService;
    private final GitHubService gitHubService;
    private final ObjectMapper objectMapper;

    public AIRepositoryMatchingService(GeminiService geminiService,
                                       GitHubService gitHubService,
                                       ObjectMapper objectMapper) {
        this.geminiService = geminiService;
        this.gitHubService = gitHubService;
        this.objectMapper = objectMapper;
    }

    /**
     * Finds and matches candidate GitHub repositories against structured requirements.
     */
    public List<AIRepositoryMatchResponse> findAndMatchRepositories(AIRequirementResponse requirements, int maxCandidates) {
        // 1. Build high-precision GitHub search query
        String searchQuery = buildGitHubSearchQuery(requirements);
        log.info("Searching GitHub for AI Project Finder candidates with query: '{}'", searchQuery);

        List<RawGitHubRepo> candidates = gitHubService.searchRepositories(searchQuery, 12);
        if (candidates.isEmpty()) {
            // Fallback broader search query using top 2 keywords
            String fallbackQuery = buildFallbackQuery(requirements);
            if (!fallbackQuery.equals(searchQuery)) {
                log.info("Refining search with fallback query: '{}'", fallbackQuery);
                candidates = gitHubService.searchRepositories(fallbackQuery, 12);
            }
        }

        // 2. Filter and rank candidate pool (limit to top candidates to optimize Gemini token and latency)
        List<RawGitHubRepo> eligible = candidates.stream()
                .filter(r -> r.getName() != null && r.getOwner() != null)
                .filter(r -> r.getDescription() != null && !r.getDescription().isBlank())
                .sorted((a, b) -> Integer.compare(
                        b.getStargazersCount() != null ? b.getStargazersCount() : 0,
                        a.getStargazersCount() != null ? a.getStargazersCount() : 0
                ))
                .limit(Math.max(2, Math.min(maxCandidates, 6)))
                .collect(Collectors.toList());

        List<AIRepositoryMatchResponse> results = new ArrayList<>();

        for (RawGitHubRepo repo : eligible) {
            try {
                AIRepositoryMatchResponse match = evaluateCandidate(repo, requirements);
                results.add(match);
            } catch (Exception e) {
                log.warn("Failed to evaluate candidate {}/{}: {}", repo.getOwner().getLogin(), repo.getName(), e.getMessage());
            }
        }

        // 3. Sort deterministically by Final Ranking Score descending
        results.sort((a, b) -> Double.compare(
                b.getFinalRankingScore() != null ? b.getFinalRankingScore() : 0.0,
                a.getFinalRankingScore() != null ? a.getFinalRankingScore() : 0.0
        ));

        return results;
    }

    /**
     * Evaluates a single candidate repository against requirements.
     */
    public AIRepositoryMatchResponse evaluateCandidate(RawGitHubRepo repo, AIRequirementResponse requirements) {
        String owner = repo.getOwner() != null ? repo.getOwner().getLogin() : "";
        String name = repo.getName();
        String fullName = owner + "/" + name;

        // Calculate push recency
        Instant now = Instant.now();
        Instant pushedAt = now.minus(30, ChronoUnit.DAYS);
        if (repo.getPushedAt() != null) {
            try {
                pushedAt = Instant.parse(repo.getPushedAt());
            } catch (Exception ignored) {}
        }
        int daysSinceLastPush = (int) Math.max(0, ChronoUnit.DAYS.between(pushedAt, now));

        // Deterministic Activity Score (0-100)
        double activityScore;
        if (daysSinceLastPush <= 7) activityScore = 95.0;
        else if (daysSinceLastPush <= 30) activityScore = 85.0;
        else if (daysSinceLastPush <= 90) activityScore = 65.0;
        else if (daysSinceLastPush <= 180) activityScore = 45.0;
        else activityScore = 20.0;

        // Deterministic baseline Sustainability Score (0-100)
        int stars = repo.getStargazersCount() != null ? repo.getStargazersCount() : 0;
        int forks = repo.getForksCount() != null ? repo.getForksCount() : 0;
        int openIssues = repo.getOpenIssuesCount() != null ? repo.getOpenIssuesCount() : 0;
        boolean isArchived = Boolean.TRUE.equals(repo.getArchived());

        double sustainabilityScore = calculateBaselineSustainability(stars, forks, openIssues, daysSinceLastPush, isArchived, repo.getLicense() != null);

        // Fetch README snippet (max 2.5KB) for evidence matching
        String readme = gitHubService.fetchReadme(owner, name);
        if (readme.length() > 2500) {
            readme = readme.substring(0, 2500) + "...";
        }

        AIRepositoryMatchResponse matchResponse;

        if (geminiService.isConfigured()) {
            matchResponse = evaluateWithGemini(repo, requirements, readme, daysSinceLastPush);
        } else {
            matchResponse = evaluateWithHeuristics(repo, requirements, readme, daysSinceLastPush);
        }

        // Attach quantitative metadata
        matchResponse.setRepositoryId(repo.getId());
        matchResponse.setOwner(owner);
        matchResponse.setName(name);
        matchResponse.setFullName(fullName);
        matchResponse.setHtmlUrl(repo.getHtmlUrl() != null ? repo.getHtmlUrl() : "https://github.com/" + fullName);
        matchResponse.setDescription(repo.getDescription());
        matchResponse.setPrimaryLanguage(repo.getLanguage() != null ? repo.getLanguage() : "Multi-language");
        matchResponse.setStarsCount(stars);
        matchResponse.setForksCount(forks);
        matchResponse.setDaysSinceLastPush(daysSinceLastPush);
        matchResponse.setSustainabilityScore(sustainabilityScore);

        // 4. Calculate Final Ranking Score:
        // Requirement Match: 40%
        // Sustainability Score: 30%
        // Domain/Tech Relevance: 20% (tailored to user's requested domain, e.g. Cloud, ML, Frontend, Backend)
        // Activity/Freshness: 10%
        double matchVal = matchResponse.getMatchScore() != null ? matchResponse.getMatchScore() : 50.0;
        double relevanceVal = matchResponse.getDomainRelevanceScore() != null
                ? matchResponse.getDomainRelevanceScore()
                : (matchResponse.getCloudRelevanceScore() != null ? matchResponse.getCloudRelevanceScore() : 50.0);

        double finalRank = (0.40 * matchVal) + (0.30 * sustainabilityScore) + (0.20 * relevanceVal) + (0.10 * activityScore);
        finalRank = Math.round(finalRank * 10.0) / 10.0;
        matchResponse.setFinalRankingScore(finalRank);

        return matchResponse;
    }

    private AIRepositoryMatchResponse evaluateWithGemini(RawGitHubRepo repo,
                                                         AIRequirementResponse requirements,
                                                         String readme,
                                                         int daysSinceLastPush) {
        try {
            Map<String, Object> candidateData = new LinkedHashMap<>();
            candidateData.put("fullName", repo.getFullName());
            candidateData.put("description", repo.getDescription());
            candidateData.put("language", repo.getLanguage());
            candidateData.put("stars", repo.getStargazersCount());
            candidateData.put("daysSinceLastPush", daysSinceLastPush);
            if (!readme.isBlank()) {
                candidateData.put("readmeSnippet", geminiService.redactSecrets(readme));
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userRequirements", requirements);
            payload.put("candidateRepository", candidateData);

            String contextJson = objectMapper.writeValueAsString(payload);

            String systemInstruction = """
                You are a Senior Technical Evaluator for OpenInfraIQ.
                Evaluate how well the candidate repository satisfies the user's specific technical requirements and needs.
                The user's requirements can span ANY software domain: Cloud Infrastructure, DevOps, Backend APIs, Frontend Web, Mobile, Full-Stack, Machine Learning / AI, Data Engineering, Microservices, Automation, Databases, or Developer Tools.

                CRITICAL INSTRUCTIONS:
                1. Only use evidence verifiable in the repository name, description, language, or README snippet.
                2. Status for each requirement MUST strictly be one of: "MATCHED" | "PARTIALLY_MATCHED" | "MISSING" | "UNKNOWN".
                3. Calculate matchScore (0 - 100) reflecting how completely requested tools/technologies/features are present.
                4. Calculate domainRelevanceScore (0 - 100) measuring how authentically and deeply the repository embodies the user's TARGET DOMAIN and needs (e.g., if user wants Machine Learning, evaluate ML/AI pipeline depth; if Frontend, evaluate UI architecture/state management; if Backend, evaluate API/data architecture; if Cloud/DevOps, evaluate IaC/K8s/CI/CD depth).
                5. Provide relevanceLabel: e.g. "Cloud Relevance", "AI/ML Relevance", "Frontend Relevance", "Backend Relevance", "Data Relevance", or "Domain Relevance".
                6. Set cloudRelevanceScore to the same numeric score as domainRelevanceScore for backward compatibility.
                7. Do NOT hallucinate. If no evidence for a requested tool exists in the repo, status is "MISSING".

                Return strictly JSON matching this structure:
                {
                  "matchScore": 88,
                  "domainRelevanceScore": 92,
                  "cloudRelevanceScore": 92,
                  "relevanceLabel": "Cloud Relevance",
                  "category": "cloud_infrastructure",
                  "requirements": [
                    {
                      "requirement": "Terraform",
                      "status": "MATCHED",
                      "confidence": 0.95,
                      "evidence": "Terraform modules and HCL configuration documented in repository."
                    }
                  ],
                  "strengths": ["Comprehensive architectural configuration", "Clear documentation and structure"],
                  "weaknesses": ["Some optional secondary tools are not included"],
                  "recommendations": ["Incorporate additional automated tests or integration scripts"],
                  "summary": "This repository matches the core technical requirements specified by the user."
                }
                """;

            String userPrompt = "Evaluate this repository against requirements:\n\n" + contextJson;

            AIRepositoryMatchResponse response = geminiService.generateStructuredContent(
                    systemInstruction,
                    userPrompt,
                    AIRepositoryMatchResponse.class
            );

            if (response != null && response.getMatchScore() != null) {
                // Ensure scores are in [0, 100]
                response.setMatchScore(Math.max(0, Math.min(100, response.getMatchScore())));
                int domScore = response.getDomainRelevanceScore() != null ? response.getDomainRelevanceScore()
                        : (response.getCloudRelevanceScore() != null ? response.getCloudRelevanceScore() : 50);
                domScore = Math.max(0, Math.min(100, domScore));
                response.setDomainRelevanceScore(domScore);
                response.setCloudRelevanceScore(domScore);
                if (response.getRelevanceLabel() == null || response.getRelevanceLabel().isBlank()) {
                    response.setRelevanceLabel(determineRelevanceLabel(response.getCategory()));
                }
                return response;
            }
        } catch (Exception e) {
            log.warn("Gemini evaluation failed for {}: {}. Using heuristic evaluation.", repo.getFullName(), e.getMessage());
        }

        return evaluateWithHeuristics(repo, requirements, readme, daysSinceLastPush);
    }

    private AIRepositoryMatchResponse evaluateWithHeuristics(RawGitHubRepo repo,
                                                             AIRequirementResponse reqs,
                                                             String readme,
                                                             int daysSinceLastPush) {
        String corpus = (repo.getName() + " " + (repo.getDescription() != null ? repo.getDescription() : "") + " " + readme).toLowerCase();

        List<AIRepositoryMatchResponse.RequirementItem> reqItems = new ArrayList<>();
        int matchedCount = 0;
        int totalReqs = 0;

        List<String> allDemands = new ArrayList<>();
        if (reqs.getCloudProviders() != null) allDemands.addAll(reqs.getCloudProviders());
        if (reqs.getRequiredTechnologies() != null) allDemands.addAll(reqs.getRequiredTechnologies());
        if (reqs.getMonitoringRequirements() != null) allDemands.addAll(reqs.getMonitoringRequirements());

        if (allDemands.isEmpty() && reqs.getKeywords() != null) {
            allDemands.addAll(reqs.getKeywords());
        }

        for (String demand : allDemands) {
            totalReqs++;
            String dLower = demand.toLowerCase();
            boolean found = corpus.contains(dLower);
            if (found) {
                matchedCount++;
                reqItems.add(AIRepositoryMatchResponse.RequirementItem.builder()
                        .requirement(demand)
                        .status("MATCHED")
                        .confidence(0.90)
                        .evidence("Evidence of " + demand + " detected in repository description or configuration.")
                        .build());
            } else {
                reqItems.add(AIRepositoryMatchResponse.RequirementItem.builder()
                        .requirement(demand)
                        .status("MISSING")
                        .confidence(0.85)
                        .evidence("No explicit evidence of " + demand + " in repository metadata.")
                        .build());
            }
        }

        int matchScore = totalReqs > 0 ? (int) Math.round(((double) matchedCount / totalReqs) * 100.0) : 75;

        // Domain-adaptive relevance calculation
        int domainScore = calculateDomainRelevanceScore(corpus, reqs);
        String relevanceLabel = determineRelevanceLabel(reqs.getCategory());

        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        if (matchScore >= 75) strengths.add("Strong alignment with requested core technical components.");
        if (domainScore >= 80) strengths.add("Deep architectural patterns detected for " + relevanceLabel.toLowerCase() + ".");
        if (matchScore < 100) weaknesses.add("Some requested technologies were not evidenced in repository documentation.");

        return AIRepositoryMatchResponse.builder()
                .matchScore(matchScore)
                .domainRelevanceScore(domainScore)
                .cloudRelevanceScore(domainScore)
                .relevanceLabel(relevanceLabel)
                .category(reqs.getCategory() != null ? reqs.getCategory() : "other")
                .requirements(reqItems)
                .strengths(strengths)
                .weaknesses(weaknesses)
                .recommendations(List.of("Review documentation and manifest folders for full configuration details."))
                .summary("Repository matches " + matchedCount + " of " + Math.max(1, totalReqs) + " requested technology requirements.")
                .build();
    }

    private String determineRelevanceLabel(String category) {
        if (category == null) return "Domain Relevance";
        return switch (category.toLowerCase()) {
            case "cloud_infrastructure", "devops" -> "Cloud Relevance";
            case "machine_learning" -> "AI/ML Relevance";
            case "frontend" -> "Frontend Relevance";
            case "backend", "microservices" -> "Backend Relevance";
            case "data_engineering", "database" -> "Data Relevance";
            case "mobile" -> "Mobile Relevance";
            default -> "Domain Relevance";
        };
    }

    private int calculateDomainRelevanceScore(String text, AIRequirementResponse reqs) {
        String cat = reqs.getCategory() != null ? reqs.getCategory().toLowerCase() : "";
        String[] keywords;
        if (cat.equals("machine_learning")) {
            keywords = new String[]{"pytorch", "tensorflow", "model", "train", "dataset", "nlp", "llm", "huggingface", "neural", "deep learning", "python", "inference"};
        } else if (cat.equals("frontend")) {
            keywords = new String[]{"react", "vue", "angular", "svelte", "vite", "nextjs", "tailwind", "css", "ui", "components", "web", "typescript", "javascript"};
        } else if (cat.equals("backend") || cat.equals("microservices")) {
            keywords = new String[]{"api", "rest", "grpc", "microservices", "database", "controller", "service", "spring", "golang", "express", "auth", "routing"};
        } else if (cat.equals("data_engineering") || cat.equals("database")) {
            keywords = new String[]{"kafka", "spark", "flink", "airflow", "pipeline", "etl", "streaming", "batch", "sql", "postgres", "mongodb", "redis"};
        } else if (cat.equals("mobile")) {
            keywords = new String[]{"flutter", "react native", "swift", "kotlin", "android", "ios", "mobile", "app", "dart"};
        } else {
            keywords = new String[]{
                    "aws", "azure", "gcp", "terraform", "kubernetes", "docker", "ansible",
                    "helm", "ci/cd", "pipeline", "prometheus", "grafana", "infrastructure",
                    "devops", "cloudformation", "pulumi", "microservices", "ingress", "istio"
            };
        }
        int hitCount = 0;
        for (String kw : keywords) {
            if (text.contains(kw)) hitCount++;
        }
        if (hitCount >= 5) return 96;
        if (hitCount >= 3) return 85;
        if (hitCount >= 2) return 72;
        if (hitCount == 1) return 55;
        return 35;
    }

    private double calculateBaselineSustainability(int stars, int forks, int issues, int daysSincePush, boolean isArchived, boolean hasLicense) {
        if (isArchived) return 20.0;
        double score = 50.0;
        if (stars > 500) score += 15.0;
        else if (stars > 50) score += 10.0;

        if (daysSincePush <= 14) score += 20.0;
        else if (daysSincePush <= 60) score += 10.0;
        else if (daysSincePush > 180) score -= 15.0;

        if (hasLicense) score += 10.0;
        if (forks > 50) score += 5.0;

        return Math.max(25.0, Math.min(96.0, Math.round(score * 10.0) / 10.0));
    }

    private String buildGitHubSearchQuery(AIRequirementResponse reqs) {
        StringBuilder sb = new StringBuilder();
        if (reqs.getCloudProviders() != null && !reqs.getCloudProviders().isEmpty()) {
            sb.append(reqs.getCloudProviders().get(0)).append(" ");
        }
        if (reqs.getRequiredTechnologies() != null && !reqs.getRequiredTechnologies().isEmpty()) {
            for (int i = 0; i < Math.min(3, reqs.getRequiredTechnologies().size()); i++) {
                sb.append(reqs.getRequiredTechnologies().get(i)).append(" ");
            }
        }
        if (sb.length() == 0 && reqs.getKeywords() != null && !reqs.getKeywords().isEmpty()) {
            for (int i = 0; i < Math.min(3, reqs.getKeywords().size()); i++) {
                sb.append(reqs.getKeywords().get(i)).append(" ");
            }
        }
        if (sb.length() == 0) {
            String cat = reqs.getCategory() != null ? reqs.getCategory().toLowerCase() : "";
            switch (cat) {
                case "frontend" -> sb.append("frontend react web");
                case "backend" -> sb.append("backend api microservice");
                case "full_stack" -> sb.append("fullstack web app");
                case "machine_learning" -> sb.append("machine learning pytorch");
                case "data_engineering" -> sb.append("data pipeline kafka");
                case "mobile" -> sb.append("mobile app flutter");
                case "database" -> sb.append("database storage engine");
                default -> sb.append("open source infrastructure project");
            }
        }
        return sb.toString().trim();
    }

    private String buildFallbackQuery(AIRequirementResponse reqs) {
        if (reqs.getKeywords() != null && !reqs.getKeywords().isEmpty()) {
            return String.join(" ", reqs.getKeywords().subList(0, Math.min(2, reqs.getKeywords().size())));
        }
        String cat = reqs.getCategory() != null ? reqs.getCategory().toLowerCase() : "";
        return switch (cat) {
            case "frontend" -> "react typescript";
            case "backend" -> "spring boot api";
            case "machine_learning" -> "pytorch machine learning";
            case "data_engineering" -> "kafka spark";
            case "full_stack" -> "fullstack web";
            default -> "devops infrastructure";
        };
    }
}
