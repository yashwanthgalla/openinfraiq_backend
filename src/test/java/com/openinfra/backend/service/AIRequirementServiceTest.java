package com.openinfra.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openinfra.backend.dto.AIRequirementRequest;
import com.openinfra.backend.dto.AIRequirementResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AIRequirementServiceTest {

    private AIRequirementService requirementService;
    private GeminiService geminiService;

    @BeforeEach
    void setUp() {
        geminiService = new GeminiService(new ObjectMapper());
        requirementService = new AIRequirementService(geminiService);
    }

    @Test
    void testFallbackDeterministicRequirementExtraction() {
        AIRequirementRequest request = new AIRequirementRequest(
                "I need an AWS infrastructure project using Terraform, Docker and Kubernetes with CI/CD and Prometheus monitoring",
                null
        );

        AIRequirementResponse response = requirementService.extractRequirements(request);

        assertNotNull(response);
        assertEquals("cloud_infrastructure", response.getCategory());
        assertTrue(response.getCloudProviders().contains("AWS"));
        assertTrue(response.getRequiredTechnologies().contains("Terraform"));
        assertTrue(response.getRequiredTechnologies().contains("Docker"));
        assertTrue(response.getRequiredTechnologies().contains("Kubernetes"));
        assertTrue(response.getMonitoringRequirements().contains("Prometheus"));
        assertTrue(response.getKeywords().contains("Terraform"));
        assertNotNull(response.getSummaryText());
    }

    @Test
    void testBlankRequirementValidation() {
        AIRequirementRequest emptyRequest = new AIRequirementRequest("   ", null);
        assertThrows(IllegalArgumentException.class, () -> requirementService.extractRequirements(emptyRequest));
    }

    @Test
    void testMultiDomainExtractionForMachineLearning() {
        AIRequirementRequest request = new AIRequirementRequest(
                "I want a Python deep learning project using PyTorch for natural language processing with HuggingFace transformers",
                null
        );

        AIRequirementResponse response = requirementService.extractRequirements(request);

        assertNotNull(response);
        assertEquals("machine_learning", response.getCategory());
        assertTrue(response.getRequiredTechnologies().contains("Pytorch"));
        assertTrue(response.getKeywords().contains("Pytorch"));
    }

    @Test
    void testMultiDomainExtractionForFrontend() {
        AIRequirementRequest request = new AIRequirementRequest(
                "I need a React frontend application with TailwindCSS and Vite",
                null
        );

        AIRequirementResponse response = requirementService.extractRequirements(request);

        assertNotNull(response);
        assertEquals("frontend", response.getCategory());
        assertTrue(response.getRequiredTechnologies().contains("React"));
        assertTrue(response.getRequiredTechnologies().contains("Vite"));
    }
}
