package com.openinfra.backend.dto;

import com.openinfra.backend.entity.User;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private Long id;
    private String uid;
    private String email;
    private String name;
    private String username;
    private String avatar;
    private String organization;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    public static UserResponse fromEntity(User user) {
        if (user == null) return null;
        return UserResponse.builder()
                .id(user.getId())
                .uid(user.getFirebaseUid())
                .email(user.getEmail())
                .name(user.getDisplayName() != null ? user.getDisplayName() : user.getEmail())
                .username(user.getUsername())
                .avatar(user.getPhotoUrl())
                .organization(user.getOrganization())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
