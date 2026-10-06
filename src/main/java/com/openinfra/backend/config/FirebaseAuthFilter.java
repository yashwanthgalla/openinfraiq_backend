package com.openinfra.backend.config;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.openinfra.backend.entity.User;
import com.openinfra.backend.security.FirebaseUserPrincipal;
import com.openinfra.backend.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class FirebaseAuthFilter extends OncePerRequestFilter {

    private final FirebaseConfig firebaseConfig;
    private final UserService userService;

    public FirebaseAuthFilter(FirebaseConfig firebaseConfig, UserService userService) {
        this.firebaseConfig = firebaseConfig;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String uidHeader = request.getHeader("X-Firebase-Uid");
        String emailHeader = request.getHeader("X-User-Email");
        String nameHeader = request.getHeader("X-User-Name");
        String usernameHeader = request.getHeader("X-User-Username");

        String firebaseUid = null;
        String email = null;
        String displayName = null;
        String photoUrl = null;

        // 1. Verify cryptographic Bearer token via Firebase Admin SDK
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String idToken = authHeader.substring(7).trim();
            if (!idToken.isEmpty()) {
                if (firebaseConfig.isFirebaseInitialized() && !FirebaseApp.getApps().isEmpty()) {
                    try {
                        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
                        firebaseUid = decodedToken.getUid();
                        email = decodedToken.getEmail();
                        displayName = decodedToken.getName();
                        photoUrl = decodedToken.getPicture();
                    } catch (Exception e) {
                        log.warn("Firebase ID Token verification rejected: {}", e.getMessage());
                    }
                }

                // Dev/Standalone JWT payload inspection fallback
                if (firebaseUid == null && idToken.contains(".")) {
                    try {
                        String[] parts = idToken.split("\\.");
                        if (parts.length >= 2) {
                            String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
                            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payloadJson);
                            if (root.has("user_id")) firebaseUid = root.get("user_id").asText();
                            else if (root.has("sub")) firebaseUid = root.get("sub").asText();
                            if (root.has("email")) email = root.get("email").asText();
                            if (root.has("name")) displayName = root.get("name").asText();
                            if (root.has("picture")) photoUrl = root.get("picture").asText();
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // 2. Fallback to developer UID header
        if (firebaseUid == null && uidHeader != null && !uidHeader.isBlank()) {
            firebaseUid = uidHeader.trim();
        }

        if (email == null && emailHeader != null && !emailHeader.isBlank()) {
            email = emailHeader.trim();
        }
        if (displayName == null && nameHeader != null && !nameHeader.isBlank()) {
            displayName = nameHeader.trim();
        }
        String username = (usernameHeader != null && !usernameHeader.isBlank()) ? usernameHeader.trim() : null;

        // 3. Authenticate and populate Spring Security Context with persistent MySQL User entity
        if (firebaseUid != null) {
            User user = userService.getOrCreateEntity(firebaseUid, email, displayName, username, null);
            if (photoUrl != null && (user.getPhotoUrl() == null || user.getPhotoUrl().isBlank())) {
                user.setPhotoUrl(photoUrl);
            }
            FirebaseUserPrincipal principal = new FirebaseUserPrincipal(user, firebaseUid, email != null ? email : user.getEmail());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
