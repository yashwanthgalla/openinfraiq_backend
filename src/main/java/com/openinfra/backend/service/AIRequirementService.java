package com.openinfra.backend.service;

import com.openinfra.backend.dto.AIRequirementRequest;
import com.openinfra.backend.dto.AIRequirementResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AIRequirementService {

    private final GeminiService geminiService;

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "cloud_infrastructure", "devops", "backend", "frontend", "full_stack",
            "microservices", "data_engineering", "machine_learning", "mobile", "automation",
            "database", "other"
    );

    public AIRequirementService(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    /**
     * Extracts structured project requirements from natural language input.
     * Uses Gemini when configured, with a deterministic multi-domain extraction fallback if offline.
     */
    public AIRequirementResponse extractRequirements(AIRequirementRequest request) {
        String query = request.getDescription() != null ? request.getDescription().trim() : "";
        if (query.isBlank()) {
            throw new IllegalArgumentException("Description cannot be empty");
        }

        if (geminiService.isConfigured()) {
            try {
                String systemInstruction = """
                    You are a Senior Principal Software Architect & Multi-Domain Technical Requirements Extractor for OpenInfraIQ.
                    Your goal is to parse user natural language project search requests and extract structured technical specifications tailored to the user's exact needs and target domain.
                    The user may ask for ANY software domain: Cloud Infrastructure, DevOps, Backend APIs, Frontend Web, Mobile, Full-Stack, Machine Learning / AI, Data Engineering, Microservices, Automation, Databases, Systems/Embedded, or Developer Tools.

                    CLASSIFICATION CATEGORIES:
                    - cloud_infrastructure
                    - devops
                    - backend
                    - frontend
                    - full_stack
                    - microservices
                    - data_engineering
                    - machine_learning
                    - mobile
                    - automation
                    - database
                    - other

                    CRITICAL RULES:
                    1. ADAPT TO USER NEEDS: Determine the category based strictly on what the user actually wants (e.g., if user asks for React or UI, choose 'frontend'; if PyTorch or LLMs, choose 'machine_learning'; if Go or Spring Boot APIs, choose 'backend' or 'microservices'; if Terraform or AWS, choose 'cloud_infrastructure' or 'devops').
                    2. Do NOT force a project to be cloud infrastructure if the user is asking for Frontend, Machine Learning, Mobile, or Backend.
                    3. Do NOT invent technologies the user did not imply.
                    4. Extract clean, high-precision search keywords suitable for GitHub query construction.
                    5. Choose the appropriate complexity (beginner | intermediate | advanced).

                    Return strictly a JSON object with this exact schema:
                    {
                      "projectType": "frontend | machine_learning | backend | cloud_infrastructure | full_stack | devops | ...",
                      "category": "frontend | machine_learning | backend | cloud_infrastructure | full_stack | devops | ...",
                      "cloudProviders": [],
                      "requiredTechnologies": ["React", "Vite", "TailwindCSS"],
                      "preferredTechnologies": ["TypeScript"],
                      "requiredFeatures": ["Component Architecture", "Responsive Design", "State Management"],
                      "monitoringRequirements": [],
                      "networkingRequirements": [],
                      "securityRequirements": [],
                      "databaseRequirements": [],
                      "deploymentRequirements": [],
                      "maintenancePreferences": [],
                      "complexity": "intermediate",
                      "keywords": ["React", "Vite", "TailwindCSS", "TypeScript"],
                      "summaryText": "Modern React frontend web application using Vite and TailwindCSS."
                    }
                    """;

                String userPrompt = "Parse the following user project requirements:\n\"" + query + "\"";

                AIRequirementResponse response = geminiService.generateStructuredContent(
                        systemInstruction,
                        userPrompt,
                        AIRequirementResponse.class
                );

                if (response != null) {
                    normalizeResponse(response, query);
                    return response;
                }
            } catch (Exception e) {
                log.warn("Gemini requirement extraction encountered an issue: {}. Falling back to deterministic extractor.", e.getMessage());
            }
        }

        // Deterministic regex/keyword fallback when Gemini is offline or unconfigured
        return fallbackDeterministicExtraction(query);
    }

    private void normalizeResponse(AIRequirementResponse res, String rawQuery) {
        res.setRawQuery(rawQuery);
        if (res.getCategory() == null || !ALLOWED_CATEGORIES.contains(res.getCategory().toLowerCase())) {
            res.setCategory("other");
        } else {
            res.setCategory(res.getCategory().toLowerCase());
        }

        if (res.getKeywords() == null || res.getKeywords().isEmpty()) {
            List<String> derived = new ArrayList<>();
            if (res.getCloudProviders() != null) derived.addAll(res.getCloudProviders());
            if (res.getRequiredTechnologies() != null) derived.addAll(res.getRequiredTechnologies());
            res.setKeywords(derived);
        }

        if (res.getSummaryText() == null || res.getSummaryText().isBlank()) {
            res.setSummaryText("Targeting " + res.getCategory() + " with " + String.join(", ", res.getRequiredTechnologies()));
        }
    }

    /**
     * Deterministic rule-based requirements extraction fallback for all software domains.
     */
    private AIRequirementResponse fallbackDeterministicExtraction(String text) {
        String lower = text.toLowerCase();
        List<String> clouds = new ArrayList<>();
        if (lower.contains("aws") || lower.contains("amazon")) clouds.add("AWS");
        if (lower.contains("azure")) clouds.add("Azure");
        if (lower.contains("gcp") || lower.contains("google cloud")) clouds.add("GCP");

        List<String> techs = new ArrayList<>();
        String[] candidates = {
                "terraform", "ansible", "docker", "kubernetes", "k8s", "helm",
                "jenkins", "github actions", "gitlab", "prometheus", "grafana",
                "argo", "argocd", "istio", "vault", "consul", "kafka", "redis",
                "postgres", "postgresql", "mysql", "mongodb", "spring boot", "react", "nextjs", "next.js",
                "vue", "angular", "svelte", "tailwind", "vite", "python", "golang", "go",
                "pytorch", "tensorflow", "fastapi", "django", "express", "flutter", "swift"
        };
        for (String c : candidates) {
            if (lower.contains(c)) {
                techs.add(capitalize(c));
            }
        }

        List<String> monitoring = new ArrayList<>();
        if (lower.contains("prometheus")) monitoring.add("Prometheus");
        if (lower.contains("grafana")) monitoring.add("Grafana");
        if (lower.contains("elk") || lower.contains("elasticsearch")) monitoring.add("ELK Stack");

        List<String> keywords = new ArrayList<>(clouds);
        keywords.addAll(techs);
        if (keywords.isEmpty()) {
            keywords.addAll(Arrays.asList(text.split("\\s+")));
        }

        // Adapt category dynamically to user requirements
        String category = "other";
        if (lower.contains("machine learning") || lower.contains("deep learning") || lower.contains("pytorch")
                || lower.contains("tensorflow") || lower.contains("llm") || lower.contains("nlp") || lower.contains("ai model")) {
            category = "machine_learning";
        } else if (lower.contains("frontend") || lower.contains("react") || lower.contains("vue")
                || lower.contains("angular") || lower.contains("svelte") || lower.contains("tailwind") || lower.contains("ui")) {
            category = "frontend";
        } else if (lower.contains("full stack") || lower.contains("fullstack") || lower.contains("mern") || lower.contains("nextjs") || lower.contains("next.js")) {
            category = "full_stack";
        } else if (lower.contains("data engineering") || lower.contains("spark") || lower.contains("flink") || lower.contains("airflow") || lower.contains("etl")) {
            category = "data_engineering";
        } else if (lower.contains("mobile") || lower.contains("flutter") || lower.contains("react native") || lower.contains("swift") || lower.contains("android") || lower.contains("ios")) {
            category = "mobile";
        } else if (lower.contains("microservice") || lower.contains("microservices")) {
            category = "microservices";
        } else if (lower.contains("backend") || lower.contains("spring boot") || lower.contains("django") || lower.contains("fastapi") || lower.contains("express") || lower.contains("api") || lower.contains("grpc")) {
            category = "backend";
        } else if (lower.contains("database") || lower.contains("postgres") || lower.contains("mysql") || lower.contains("mongodb") || lower.contains("redis")) {
            category = "database";
        } else if (lower.contains("cloud") || lower.contains("infrastructure") || lower.contains("aws") || lower.contains("azure") || lower.contains("gcp") || lower.contains("terraform")) {
            category = "cloud_infrastructure";
        } else if (lower.contains("devops") || lower.contains("ci/cd") || lower.contains("pipeline") || lower.contains("ansible") || lower.contains("jenkins") || lower.contains("github actions") || lower.contains("kubernetes") || lower.contains("k8s") || lower.contains("docker")) {
            category = "devops";
        }

        List<String> features = new ArrayList<>();
        if (category.equals("cloud_infrastructure") || category.equals("devops")) {
            features.add("Infrastructure as Code");
            features.add("Containerization");
        } else if (category.equals("frontend")) {
            features.add("Component Architecture");
            features.add("Responsive UI");
        } else if (category.equals("machine_learning")) {
            features.add("Model Pipeline");
            features.add("Data Processing");
        } else if (category.equals("backend") || category.equals("microservices")) {
            features.add("REST/API Services");
            features.add("Persistence");
        } else {
            features.add("Modular Architecture");
        }

        return AIRequirementResponse.builder()
                .projectType(category)
                .category(category)
                .cloudProviders(clouds)
                .requiredTechnologies(techs)
                .preferredTechnologies(Collections.emptyList())
                .requiredFeatures(features)
                .monitoringRequirements(monitoring)
                .networkingRequirements(Collections.emptyList())
                .securityRequirements(Collections.emptyList())
                .databaseRequirements(Collections.emptyList())
                .deploymentRequirements(clouds)
                .maintenancePreferences(Collections.emptyList())
                .complexity(lower.contains("beginner") ? "beginner" : (lower.contains("advanced") ? "advanced" : "intermediate"))
                .keywords(keywords)
                .rawQuery(text)
                .summaryText("Requirement extracted via deterministic heuristic engine: " + String.join(", ", keywords))
                .build();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        if (str.equalsIgnoreCase("k8s")) return "Kubernetes";
        if (str.equalsIgnoreCase("gcp")) return "GCP";
        if (str.equalsIgnoreCase("aws")) return "AWS";
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
