package com.openinfra.backend.controller;

import com.openinfra.backend.dto.ApiResponse;
import com.openinfra.backend.dto.UserProfileUpdateRequest;
import com.openinfra.backend.dto.UserResponse;
import com.openinfra.backend.security.FirebaseUserPrincipal;
import com.openinfra.backend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Slf4j
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<UserResponse>> syncUser(@jakarta.validation.Valid @RequestBody com.openinfra.backend.dto.UserSyncRequest request) {
        log.info("Received user sync request via /api/users/sync for UID: {}", request.getUid());
        UserResponse response = userService.syncUser(request);
        return ResponseEntity.ok(ApiResponse.ok("User profile synced successfully", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUserProfile(
            @AuthenticationPrincipal FirebaseUserPrincipal principal
    ) {
        UserResponse response = userService.getUserByFirebaseUid(principal.getFirebaseUid());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateCurrentUserProfile(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @RequestBody UserProfileUpdateRequest request
    ) {
        UserResponse response = userService.updateProfile(principal.getFirebaseUid(), request);
        return ResponseEntity.ok(ApiResponse.ok("User profile updated successfully", response));
    }

    @GetMapping("/{firebaseUid}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByUid(
            @AuthenticationPrincipal FirebaseUserPrincipal principal,
            @PathVariable String firebaseUid
    ) {
        UserResponse response = userService.getUserByFirebaseUid(firebaseUid);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
