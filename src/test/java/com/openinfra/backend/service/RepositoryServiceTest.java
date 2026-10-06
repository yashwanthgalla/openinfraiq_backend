package com.openinfra.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openinfra.backend.dto.RepositoryResponse;
import com.openinfra.backend.entity.Repository;
import com.openinfra.backend.entity.User;
import com.openinfra.backend.exception.ResourceNotFoundException;
import com.openinfra.backend.repository.RepositoryAnalysisRepository;
import com.openinfra.backend.repository.RepositoryRepository;
import com.openinfra.backend.repository.SearchHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryServiceTest {

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private RepositoryAnalysisRepository analysisRepository;

    @Mock
    private SearchHistoryRepository searchHistoryRepository;

    @Mock
    private GitHubService gitHubService;

    @Mock
    private AnalysisEngineService analysisEngineService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RepositoryService repositoryService;

    private User userA;
    private User userB;
    private Repository repoA;

    @BeforeEach
    void setUp() {
        userA = User.builder().id(1L).firebaseUid("uid_user_a").email("userA@test.org").build();
        userB = User.builder().id(2L).firebaseUid("uid_user_b").email("userB@test.org").build();

        repoA = Repository.builder()
                .id(101L)
                .user(userA)
                .owner("kubernetes")
                .name("kubernetes")
                .fullName("kubernetes/kubernetes")
                .url("https://github.com/kubernetes/kubernetes")
                .build();
    }

    @Test
    void testGetUserRepositories_ReturnsOnlyUserARepositories() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        when(repositoryRepository.findByUserOrderByUpdatedAtDesc(userA, pageRequest))
                .thenReturn(new PageImpl<>(List.of(repoA)));

        Page<RepositoryResponse> result = repositoryService.getUserRepositories(userA, pageRequest);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("kubernetes/kubernetes", result.getContent().get(0).getFullName());
        verify(repositoryRepository, times(1)).findByUserOrderByUpdatedAtDesc(userA, pageRequest);
    }

    @Test
    void testGetRepositoryById_UserACanAccessOwnRepo() {
        when(repositoryRepository.findByUserAndId(userA, 101L)).thenReturn(Optional.of(repoA));

        RepositoryResponse response = repositoryService.getRepositoryById(userA, 101L);

        assertNotNull(response);
        assertEquals(101L, response.getId());
        assertEquals("kubernetes", response.getName());
    }

    @Test
    void testGetRepositoryById_UserBCannotAccessUserARepo() {
        when(repositoryRepository.findByUserAndId(userB, 101L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            repositoryService.getRepositoryById(userB, 101L);
        });
    }

    @Test
    void testDeleteRepositoryById_EnforcesOwnership() {
        when(repositoryRepository.deleteByUserAndId(userB, 101L)).thenReturn(0);

        assertThrows(ResourceNotFoundException.class, () -> {
            repositoryService.deleteRepositoryById(userB, 101L);
        });
    }

    @Test
    void testGetRepositoryAnalyses_UserBCannotAccessUserAAnalyses() {
        when(repositoryRepository.findByUserAndId(userB, 101L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            repositoryService.getRepositoryAnalyses(userB, 101L, PageRequest.of(0, 10));
        });
        verifyNoInteractions(analysisRepository);
    }
}
