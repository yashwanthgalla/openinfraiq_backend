package com.openinfra.backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileUpdateRequest {
    private String name;
    private String username;
    private String organization;
    private String role;
    private String avatar;
}
