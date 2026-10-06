package com.openinfra.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openinfra.backend.dto.SearchHistoryRequest;
import com.openinfra.backend.dto.SearchHistoryResponse;
import com.openinfra.backend.entity.Repository;
import com.openinfra.backend.entity.SearchHistory;
import com.openinfra.backend.entity.User;
import com.openinfra.backend.repository.RepositoryAnalysisRepository;
import com.openinfra.backend.repository.SearchHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchHistoryServiceTest {

    @Mock
    private SearchHistoryRepository searchHistoryRepository;

    @Mock
    private UserService userService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private RepositoryAnalysisRepository analysisRepository;

    @Mock
    private AnalysisService analysisService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SearchHistoryService searchHistoryService;

    @Test
    void testRecordSearch() {
        User user = User.builder().id(1L).firebaseUid("fb_123").email("dev@test.org").build();
        Repository repo = Repository.builder()
                .id(10L)
                .owner("facebook")
                .name("react")
                .fullName("facebook/react")
                .url("https://github.com/facebook/react")
                .primaryLanguage("JavaScript")
                .build();

        when(userService.getOrCreateEntity("fb_123", null, null)).thenReturn(user);
        when(repositoryService.findOrCreateRepository(eq("facebook"), eq("react"), any(), any(), any())).thenReturn(repo);
        when(analysisRepository.findLatestByOwnerAndName("facebook", "react")).thenReturn(Optional.empty());
        when(searchHistoryRepository.findByUserAndRepository(user, repo)).thenReturn(Optional.empty());

        when(searchHistoryRepository.save(any(SearchHistory.class))).thenAnswer(invocation -> {
            SearchHistory sh = invocation.getArgument(0);
            sh.setId(100L);
            sh.setSearchedAt(LocalDateTime.now());
            sh.setLastViewedAt(LocalDateTime.now());
            return sh;
        });

        SearchHistoryRequest req = SearchHistoryRequest.builder()
                .owner("facebook")
                .repositoryName("react")
                .repositoryUrl("https://github.com/facebook/react")
                .status("available")
                .language("JavaScript")
                .build();

        SearchHistoryResponse response = searchHistoryService.recordSearch("fb_123", req);

        assertNotNull(response);
        assertEquals("facebook", response.getOwner());
        assertEquals("react", response.getRepositoryName());
        assertEquals("https://github.com/facebook/react", response.getRepositoryUrl());
        assertEquals("available", response.getStatus());
        assertEquals("JavaScript", response.getLanguage());
    }
}
