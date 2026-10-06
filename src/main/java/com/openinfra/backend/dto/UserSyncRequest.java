package com.openinfra.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSyncRequest {

    @NotBlank(message = "Firebase UID is required")
    private String uid;

    private String email;

    private String name;

    private String fullname;

    private String username;

    private String password;

    private String photoUrl;

    private String avatar;

    private String organization;

    private String role;

    public String getDisplayName() {
        if (this.fullname != null && !this.fullname.isBlank()) {
            return this.fullname.trim();
        }
        if (this.name != null && !this.name.isBlank()) {
            return this.name.trim();
        }
        return null;
    }
}
