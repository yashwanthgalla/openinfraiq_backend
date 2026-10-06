package com.openinfra.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeminiServiceTest {

    private GeminiService geminiService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        geminiService = new GeminiService(objectMapper);
    }

    @Test
    void testSecretRedaction() {
        String sensitive = "Deploying with AKIAIOSFODNN7EXAMPLE and secret aws_secret_access_key='wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY' and token ghp_123456789012345678901234567890123456";
        String redacted = geminiService.redactSecrets(sensitive);

        assertFalse(redacted.contains("AKIAIOSFODNN7EXAMPLE"));
        assertFalse(redacted.contains("wJalrXUtnFEMI"));
        assertFalse(redacted.contains("ghp_123456789012345678901234567890123456"));
        assertTrue(redacted.contains("[REDACTED_SECRET]"));
    }

    @Test
    void testCleanJsonCodeBlocks() {
        String markdown = "```json\n{\"executiveSummary\":\"Strong infrastructure repo\"}\n```";
        String cleaned = GeminiService.cleanJsonCodeBlocks(markdown);
        assertEquals("{\"executiveSummary\":\"Strong infrastructure repo\"}", cleaned);

        String plain = "{\"simple\":\"json\"}";
        assertEquals(plain, GeminiService.cleanJsonCodeBlocks(plain));
    }

    @Test
    void testMissingApiKeyValidation() {
        // Without GEMINI_API_KEY set, generating content should throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> {
            geminiService.generateContent("instruction", "prompt", true);
        });
    }
}
