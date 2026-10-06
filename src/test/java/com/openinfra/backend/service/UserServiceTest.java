package com.openinfra.backend.service;

import com.openinfra.backend.dto.UserResponse;
import com.openinfra.backend.dto.UserSyncRequest;
import com.openinfra.backend.entity.User;
import com.openinfra.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private UserSyncRequest syncRequest;

    @BeforeEach
    void setUp() {
        syncRequest = UserSyncRequest.builder()
                .uid("fb_uid_12345")
                .email("alex.chen@infra.org")
                .name("Alex Chen")
                .username("alexchen")
                .password("SecretPassword123!")
                .organization("Cloud Platform")
                .role("Software Architect")
                .build();
    }

    @Test
    void testSyncUser_NewUser() {
        when(userRepository.findByFirebaseUid("fb_uid_12345")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });

        UserResponse response = userService.syncUser(syncRequest);

        assertNotNull(response);
        assertEquals("fb_uid_12345", response.getUid());
        assertEquals("alex.chen@infra.org", response.getEmail());
        assertEquals("Alex Chen", response.getName());
        assertEquals("alexchen", response.getUsername());
        assertEquals("Cloud Platform", response.getOrganization());
        assertEquals("Software Architect", response.getRole());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testSyncUser_ExistingUser() {
        User existingUser = User.builder()
                .id(1L)
                .firebaseUid("fb_uid_12345")
                .email("old@infra.org")
                .displayName("Old Name")
                .build();

        when(userRepository.findByFirebaseUid("fb_uid_12345")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.syncUser(syncRequest);

        assertNotNull(response);
        assertEquals("alex.chen@infra.org", response.getEmail());
        assertEquals("Alex Chen", response.getName());
        verify(userRepository, times(1)).save(existingUser);
    }
}
