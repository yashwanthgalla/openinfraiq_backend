package com.openinfra.backend.controller;

import com.openinfra.backend.dto.ApiResponse;
import com.openinfra.backend.dto.UserResponse;
import com.openinfra.backend.dto.UserSyncRequest;
import com.openinfra.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<UserResponse>> syncUser(@Valid @RequestBody UserSyncRequest request) {
        log.info("Received user sync request for UID: {}", request.getUid());
        UserResponse response = userService.syncUser(request);
        return ResponseEntity.ok(ApiResponse.ok("User profile synced successfully", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            Authentication authentication,
            @RequestHeader(value = "X-Firebase-Uid", required = false) String headerUid
    ) {
        String uid = null;
        if (authentication != null && authentication.getPrincipal() instanceof com.openinfra.backend.security.FirebaseUserPrincipal principal) {
            uid = principal.getFirebaseUid();
        } else if (authentication != null && authentication.getPrincipal() instanceof String s) {
            uid = s;
        } else if (headerUid != null && !headerUid.isBlank()) {
            uid = headerUid;
        }

        if (uid == null || uid.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.error("User is not authenticated"));
        }

        UserResponse response = userService.getUserByFirebaseUid(uid);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
