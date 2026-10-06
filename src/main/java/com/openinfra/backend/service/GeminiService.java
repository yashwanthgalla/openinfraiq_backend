package com.openinfra.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@Slf4j
public class GeminiService {

    @Value("${GEMINI_API_KEY:#{environment.GEMINI_API_KEY}}")
    private String envGeminiKey;

    @Value("${gemini.api.key:}")
    private String propGeminiKey;

    @Value("${gemini.model:gemini-3.5-flash}")
    private String modelName;

    @Value("${gemini.timeout.ms:25000}")
    private int timeoutMs;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    // Cache with 2-hour TTL to prevent redundant model calls
    private final Map<String, CacheEntry> responseCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_SECONDS = 7200;

    // Secret Detection & Redaction Patterns
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("AKIA[0-9A-Z]{16}"),                                                // AWS Access Key
            Pattern.compile("(?i)(aws_secret_access_key|aws_access_key_id)\\s*[:=]\\s*['\"]?[A-Za-z0-9/+=]{16,}['\"]?"),
            Pattern.compile("ghp_[a-zA-Z0-9]{36}"),                                            // GitHub PAT classic
            Pattern.compile("github_pat_[a-zA-Z0-9_]{82}"),                                    // GitHub Fine-grained PAT
            Pattern.compile("AIza[0-9A-Za-z-_]{35}"),                                          // Google API key
            Pattern.compile("-----BEGIN [A-Z ]+PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]+PRIVATE KEY-----"), // PEM private keys
            Pattern.compile("(?i)(password|secret|passwd|token|api_key|client_secret)\\s*[:=]\\s*['\"][^'\"]{4,}['\"]")
    );

    public GeminiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(25000);
        this.restTemplate = new RestTemplate(factory);
    }

    public String getResolvedApiKey() {
        if (envGeminiKey != null && !envGeminiKey.isBlank()) {
            return envGeminiKey.trim();
        }
        if (propGeminiKey != null && !propGeminiKey.isBlank()) {
            return propGeminiKey.trim();
        }
        return null;
    }

    public boolean isConfigured() {
        String key = getResolvedApiKey();
        return key != null && !key.isBlank() && !key.equalsIgnoreCase("test-gemini-key");
    }

    public String getModelName() {
        return modelName != null && !modelName.isBlank() ? modelName.trim() : "gemini-3.5-flash";
    }

    /**
     * Detects and redacts any sensitive API keys, passwords, or tokens prior to transmission.
     */
    public String redactSecrets(String input) {
        if (input == null || input.isBlank()) return "";
        String sanitized = input;
        for (Pattern pattern : SECRET_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("[REDACTED_SECRET]");
        }
        return sanitized;
    }

    /**
     * Executes structured content generation via Google Gemini REST API v1beta.
     */
    public String generateContent(String systemInstruction, String userPrompt, boolean forceJson) {
        String apiKey = getResolvedApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Google Gemini API key is not configured. Set GEMINI_API_KEY environment variable.");
        }

        String sanitizedPrompt = redactSecrets(userPrompt);
        String cacheKey = computeCacheKey(systemInstruction, sanitizedPrompt, forceJson);

        CacheEntry cached = responseCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Returning cached Gemini response for key hash {}", cacheKey.substring(0, 8));
            return cached.responseBody;
        }

        List<String> candidateModels = new ArrayList<>();
        String primaryModel = getModelName();
        candidateModels.add(primaryModel);
        if (!candidateModels.contains("gemini-3.5-flash")) {
            candidateModels.add("gemini-3.5-flash");
        }
        if (!candidateModels.contains("gemini-3.5-flash-lite")) {
            candidateModels.add("gemini-3.5-flash-lite");
        }

        Exception lastException = null;

        for (String effectiveModel : candidateModels) {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + effectiveModel + ":generateContent?key=" + apiKey;

            try {
                Map<String, Object> requestBody = new LinkedHashMap<>();

                // User prompt contents
                List<Map<String, Object>> contents = List.of(
                        Map.of("role", "user", "parts", List.of(Map.of("text", sanitizedPrompt)))
                );
                requestBody.put("contents", contents);

                // System instructions
                if (systemInstruction != null && !systemInstruction.isBlank()) {
                    requestBody.put("systemInstruction", Map.of(
                            "parts", List.of(Map.of("text", systemInstruction))
                    ));
                }

                // Generation config
                Map<String, Object> generationConfig = new LinkedHashMap<>();
                generationConfig.put("temperature", 0.2);
                generationConfig.put("maxOutputTokens", 4096);
                if (forceJson) {
                    generationConfig.put("responseMimeType", "application/json");
                }
                requestBody.put("generationConfig", generationConfig);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                log.info("Dispatching prompt to Google Gemini model: {}", effectiveModel);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    throw new RuntimeException("Gemini returned non-200 HTTP status: " + response.getStatusCode());
                }

                String rawText = extractTextFromGeminiResponse(response.getBody());
                String cleanedText = cleanJsonCodeBlocks(rawText);

                // Store in cache
                responseCache.put(cacheKey, new CacheEntry(cleanedText, Instant.now().plusSeconds(CACHE_TTL_SECONDS)));

                return cleanedText;

            } catch (HttpClientErrorException ex) {
                if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn("Gemini API rate limit reached (HTTP 429)");
                    throw new RuntimeException("Gemini API rate limit reached. Please retry in a few moments.", ex);
                }
                if (ex.getStatusCode() == HttpStatus.FORBIDDEN || ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    log.error("Gemini API authentication rejected (HTTP {}): {}", ex.getStatusCode(), ex.getMessage());
                    throw new RuntimeException("Invalid Gemini API credentials. Check GEMINI_API_KEY.", ex);
                }
                if (ex.getStatusCode() == HttpStatus.NOT_FOUND && !effectiveModel.equals(candidateModels.get(candidateModels.size() - 1))) {
                    log.warn("Gemini model {} not found (HTTP 404), trying fallback candidate...", effectiveModel);
                    lastException = ex;
                    continue;
                }
                log.error("Gemini client error (HTTP {}): {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                throw new RuntimeException("Gemini API client error: " + ex.getMessage(), ex);

            } catch (HttpServerErrorException ex) {
                log.warn("Gemini server error for model {} (HTTP {}), checking fallback...", effectiveModel, ex.getStatusCode());
                lastException = ex;
                if (!effectiveModel.equals(candidateModels.get(candidateModels.size() - 1))) {
                    continue;
                }
                throw new RuntimeException("Google Gemini API is temporarily unavailable.", ex);

            } catch (ResourceAccessException ex) {
                log.error("Gemini connection timed out: {}", ex.getMessage());
                throw new RuntimeException("Connection to Google Gemini timed out after " + timeoutMs + "ms.", ex);

            } catch (Exception ex) {
                log.error("Failed to execute Gemini generateContent with model {}: {}", effectiveModel, ex.getMessage(), ex);
                lastException = ex;
                if (!effectiveModel.equals(candidateModels.get(candidateModels.size() - 1))) {
                    continue;
                }
                throw new RuntimeException("Gemini communication failed: " + ex.getMessage(), ex);
            }
        }

        throw new RuntimeException("All Google Gemini candidate models failed.", lastException);
    }

    /**
     * Generates structured output parsed directly into the target Java Class.
     */
    public <T> T generateStructuredContent(String systemInstruction, String userPrompt, Class<T> targetClass) {
        String jsonText = generateContent(systemInstruction, userPrompt, true);
        try {
            return objectMapper.readValue(jsonText, targetClass);
        } catch (Exception e) {
            log.warn("Failed to parse Gemini JSON into {}: {}. Attempting sanitization.", targetClass.getSimpleName(), e.getMessage());
            String sanitized = cleanJsonCodeBlocks(jsonText);
            try {
                return objectMapper.readValue(sanitized, targetClass);
            } catch (Exception fatal) {
                log.error("Unrecoverable JSON deserialization error for {}: {}", targetClass.getSimpleName(), fatal.getMessage());
                throw new RuntimeException("Failed to parse Gemini response into " + targetClass.getSimpleName() + ": " + fatal.getMessage(), fatal);
            }
        }
    }

    private String extractTextFromGeminiResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText("");
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing candidates from Gemini response: {}", e.getMessage());
        }
        return responseJson;
    }

    public static String cleanJsonCodeBlocks(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private String computeCacheKey(String system, String prompt, boolean forceJson) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(system != null ? system.getBytes(StandardCharsets.UTF_8) : new byte[0]);
            md.update(prompt != null ? prompt.getBytes(StandardCharsets.UTF_8) : new byte[0]);
            md.update(forceJson ? (byte) 1 : (byte) 0);
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            return String.valueOf(Objects.hash(system, prompt, forceJson));
        }
    }

    private static class CacheEntry {
        final String responseBody;
        final Instant expiresAt;

        CacheEntry(String responseBody, Instant expiresAt) {
            this.responseBody = responseBody;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
