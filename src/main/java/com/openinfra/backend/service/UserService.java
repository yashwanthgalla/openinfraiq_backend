package com.openinfra.backend.service;

import com.openinfra.backend.dto.UserProfileUpdateRequest;
import com.openinfra.backend.dto.UserResponse;
import com.openinfra.backend.dto.UserSyncRequest;
import com.openinfra.backend.entity.User;
import com.openinfra.backend.exception.ResourceNotFoundException;
import com.openinfra.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse syncUser(UserSyncRequest request) {
        String uid = request.getUid();
        log.info("Syncing Firebase user with UID: {}", uid);

        String effectiveName = request.getDisplayName();
        String effectiveUsername = request.getUsername();
        if ((effectiveUsername == null || effectiveUsername.isBlank()) && request.getEmail() != null && request.getEmail().contains("@")) {
            effectiveUsername = request.getEmail().split("@")[0].replaceAll("[^a-zA-Z0-9_]", "");
        }

        User user = userRepository.findByFirebaseUid(uid)
                .orElseGet(() -> {
                    log.info("Creating new user for Firebase UID: {}", uid);
                    return User.builder()
                            .firebaseUid(uid)
                            .email(request.getEmail() != null ? request.getEmail() : uid + "@firebase.user")
                            .displayName(request.getDisplayName() != null ? request.getDisplayName() : "User")
                            .username(request.getUsername())
                            .password(request.getPassword())
                            .photoUrl(request.getPhotoUrl() != null ? request.getPhotoUrl() : request.getAvatar())
                            .organization(request.getOrganization())
                            .role(request.getRole() != null ? request.getRole() : "Developer")
                            .build();
                });

        // Update latest metadata
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            user.setEmail(request.getEmail().trim());
        }
        if (effectiveName != null && !effectiveName.isBlank()) {
            user.setDisplayName(effectiveName.trim());
        }
        if (effectiveUsername != null && !effectiveUsername.isBlank()) {
            user.setUsername(effectiveUsername.trim());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(request.getPassword());
        }
        String avatar = request.getPhotoUrl() != null ? request.getPhotoUrl() : request.getAvatar();
        if (avatar != null && !avatar.isBlank()) {
            user.setPhotoUrl(avatar.trim());
        }
        if (request.getOrganization() != null && !request.getOrganization().isBlank()) {
            user.setOrganization(request.getOrganization().trim());
        }
        if (request.getRole() != null && !request.getRole().isBlank()) {
            user.setRole(request.getRole().trim());
        }
        user.setLastLoginAt(LocalDateTime.now());

        User saved = userRepository.save(user);
        return UserResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByFirebaseUid(String firebaseUid) {
        User user = userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with Firebase UID: " + firebaseUid));
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse updateProfile(String firebaseUid, UserProfileUpdateRequest request) {
        User user = userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with Firebase UID: " + firebaseUid));

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setDisplayName(request.getName().trim());
        }
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            user.setUsername(request.getUsername().trim());
        }
        if (request.getOrganization() != null) {
            user.setOrganization(request.getOrganization().trim());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole().trim());
        }
        if (request.getAvatar() != null && !request.getAvatar().isBlank()) {
            user.setPhotoUrl(request.getAvatar().trim());
        }

        User updated = userRepository.save(user);
        return UserResponse.fromEntity(updated);
    }

    @Transactional
    public User getOrCreateEntity(String firebaseUid, String email, String name) {
        return getOrCreateEntity(firebaseUid, email, name, null, null);
    }

    @Transactional
    public User getOrCreateEntity(String firebaseUid, String email, String name, String username, String password) {
        final String effectiveEmail = (email != null && !email.isBlank()) ? email.trim() : null;
        final String effectiveName = (name != null && !name.isBlank()) ? name.trim() : null;
        final String effectiveUsername = (username != null && !username.isBlank())
                ? username.trim()
                : (effectiveEmail != null && effectiveEmail.contains("@")
                        ? effectiveEmail.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "")
                        : null);

        return userRepository.findByFirebaseUid(firebaseUid)
                .map(user -> {
                    boolean changed = false;
                    if (effectiveEmail != null && (user.getEmail() == null || user.getEmail().contains("@firebase.user"))) {
                        user.setEmail(effectiveEmail);
                        changed = true;
                    }
                    if (effectiveName != null && (user.getDisplayName() == null || "User".equals(user.getDisplayName()))) {
                        user.setDisplayName(effectiveName);
                        changed = true;
                    }
                    if (effectiveUsername != null && (user.getUsername() == null || user.getUsername().isBlank())) {
                        user.setUsername(effectiveUsername);
                        changed = true;
                    }
                    if (password != null && !password.isBlank() && (user.getPassword() == null || user.getPassword().isBlank())) {
                        user.setPassword(password);
                        changed = true;
                    }
                    return changed ? userRepository.save(user) : user;
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .firebaseUid(firebaseUid)
                        .email(effectiveEmail != null ? effectiveEmail : firebaseUid + "@firebase.user")
                        .displayName(effectiveName != null ? effectiveName : "User")
                        .username(effectiveUsername)
                        .password(password)
                        .role("Developer")
                        .build()));
    }
}
