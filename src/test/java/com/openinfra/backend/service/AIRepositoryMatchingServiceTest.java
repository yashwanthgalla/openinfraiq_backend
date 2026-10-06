package com.openinfra.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openinfra.backend.dto.AIRequirementResponse;
import com.openinfra.backend.dto.AIRepositoryMatchResponse;
import com.openinfra.backend.service.GitHubService.RawGitHubRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AIRepositoryMatchingServiceTest {

    private AIRepositoryMatchingService matchingService;
    private GitHubService gitHubService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        GeminiService geminiService = new GeminiService(objectMapper);
        gitHubService = new GitHubService();
        matchingService = new AIRepositoryMatchingService(geminiService, gitHubService, objectMapper);
    }

    @Test
    void testCandidateEvaluationAndDeterministicRanking() {
        RawGitHubRepo repo = new RawGitHubRepo();
        repo.setId(101L);
        repo.setName("terraform-aws-eks-cluster");
        repo.setDescription("Production-ready AWS infrastructure with Terraform, Kubernetes, and Docker CI/CD");
        repo.setLanguage("HCL");
        repo.setStargazersCount(1500);
        repo.setForksCount(450);
        repo.setOpenIssuesCount(12);
        repo.setPushedAt(java.time.Instant.now().toString());

        RawGitHubRepo.Owner owner = new RawGitHubRepo.Owner();
        owner.setLogin("cloud-experts");
        repo.setOwner(owner);

        AIRequirementResponse reqs = AIRequirementResponse.builder()
                .category("cloud_infrastructure")
                .cloudProviders(List.of("AWS"))
                .requiredTechnologies(List.of("Terraform", "Kubernetes", "Docker"))
                .monitoringRequirements(List.of("Prometheus"))
                .keywords(List.of("AWS", "Terraform", "Kubernetes"))
                .build();

        AIRepositoryMatchResponse match = matchingService.evaluateCandidate(repo, reqs);

        assertNotNull(match);
        assertEquals("cloud-experts/terraform-aws-eks-cluster", match.getFullName());
        assertTrue(match.getMatchScore() > 0 && match.getMatchScore() <= 100);
        assertTrue(match.getCloudRelevanceScore() >= 70);
        assertTrue(match.getSustainabilityScore() > 50);
        assertNotNull(match.getFinalRankingScore());
        assertTrue(match.getFinalRankingScore() > 50.0);
    }

    @Test
    void testMultiDomainCandidateEvaluationFrontend() {
        RawGitHubRepo repo = new RawGitHubRepo();
        repo.setId(202L);
        repo.setName("react-enterprise-dashboard");
        repo.setDescription("Modern React 19 web application with TailwindCSS, Vite, and TypeScript");
        repo.setLanguage("TypeScript");
        repo.setStargazersCount(2200);
        repo.setForksCount(310);
        repo.setOpenIssuesCount(8);
        repo.setPushedAt(java.time.Instant.now().toString());

        RawGitHubRepo.Owner owner = new RawGitHubRepo.Owner();
        owner.setLogin("web-devs");
        repo.setOwner(owner);

        AIRequirementResponse reqs = AIRequirementResponse.builder()
                .category("frontend")
                .requiredTechnologies(List.of("React", "Tailwind", "Vite"))
                .keywords(List.of("React", "Tailwind", "TypeScript"))
                .build();

        AIRepositoryMatchResponse match = matchingService.evaluateCandidate(repo, reqs);

        assertNotNull(match);
        assertEquals("web-devs/react-enterprise-dashboard", match.getFullName());
        assertEquals("frontend", match.getCategory());
        assertEquals("Frontend Relevance", match.getRelevanceLabel());
        assertTrue(match.getDomainRelevanceScore() >= 70);
        assertTrue(match.getMatchScore() > 0);
        assertTrue(match.getFinalRankingScore() > 50.0);
    }
}
