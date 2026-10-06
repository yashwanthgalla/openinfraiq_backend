package com.openinfra.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${FIREBASE_PROJECT_ID:#{environment.FIREBASE_PROJECT_ID}}")
    private String envProjectId;

    @Value("${FIREBASE_CLIENT_EMAIL:#{environment.FIREBASE_CLIENT_EMAIL}}")
    private String envClientEmail;

    @Value("${FIREBASE_PRIVATE_KEY:#{environment.FIREBASE_PRIVATE_KEY}}")
    private String envPrivateKey;

    @Value("${firebase.credentials.path:}")
    private String credentialsPath;

    private boolean firebaseInitialized = false;

    @PostConstruct
    public void initFirebase() {
        if (!FirebaseApp.getApps().isEmpty()) {
            firebaseInitialized = true;
            return;
        }

        try {
            InputStream serviceAccount = null;

            // 1. Try Environment Variables (standard for Railway / production)
            if (envProjectId != null && !envProjectId.isBlank() &&
                envClientEmail != null && !envClientEmail.isBlank() &&
                envPrivateKey != null && !envPrivateKey.isBlank()) {

                String privateKeyFormatted = envPrivateKey.replace("\\n", "\n");
                String jsonCredentials = String.format(
                        "{\n" +
                        "  \"type\": \"service_account\",\n" +
                        "  \"project_id\": \"%s\",\n" +
                        "  \"client_email\": \"%s\",\n" +
                        "  \"private_key\": \"%s\"\n" +
                        "}",
                        envProjectId, envClientEmail, privateKeyFormatted
                );
                serviceAccount = new ByteArrayInputStream(jsonCredentials.getBytes(StandardCharsets.UTF_8));
                log.info("Initialized Firebase credentials from environment variables (Project ID: {})", envProjectId);
            }

            // 2. Try file path from configuration
            if (serviceAccount == null && credentialsPath != null && !credentialsPath.isBlank()) {
                File file = new File(credentialsPath);
                if (file.exists()) {
                    serviceAccount = new FileInputStream(file);
                    log.info("Loading Firebase credentials from file path: {}", credentialsPath);
                } else {
                    serviceAccount = getClass().getClassLoader().getResourceAsStream(credentialsPath);
                    if (serviceAccount != null) {
                        log.info("Loading Firebase credentials from classpath resource: {}", credentialsPath);
                    }
                }
            }

            // 3. Try default classpath resource serviceAccountKey.json
            if (serviceAccount == null) {
                serviceAccount = getClass().getClassLoader().getResourceAsStream("serviceAccountKey.json");
                if (serviceAccount != null) {
                    log.info("Found serviceAccountKey.json in classpath resources.");
                }
            }

            if (serviceAccount != null) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                firebaseInitialized = true;
                log.info("Firebase Admin SDK successfully initialized and active.");
            } else {
                log.warn("No Firebase service account credentials found. " +
                        "Running in standalone development mode (supporting Authorization Bearer tokens and client UID mappings).");
            }
        } catch (Exception e) {
            log.warn("Firebase initialization skipped or failed: {}. Continuing in standalone development mode.", e.getMessage());
        }
    }

    public boolean isFirebaseInitialized() {
        return firebaseInitialized;
    }
}
