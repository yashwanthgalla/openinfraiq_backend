package com.openinfra.backend.service;

import com.openinfra.backend.dto.SavedRepositoryRequest;
import com.openinfra.backend.dto.SavedRepositoryResponse;
import com.openinfra.backend.entity.Repository;
import com.openinfra.backend.entity.RepositoryAnalysis;
import com.openinfra.backend.entity.SavedRepository;
import com.openinfra.backend.entity.User;
import com.openinfra.backend.exception.ResourceNotFoundException;
import com.openinfra.backend.repository.RepositoryAnalysisRepository;
import com.openinfra.backend.repository.SavedRepositoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SavedRepositoryService {

    private final SavedRepositoryRepository savedRepositoryRepository;
    private final UserService userService;
    private final RepositoryService repositoryService;
    private final RepositoryAnalysisRepository analysisRepository;

    public SavedRepositoryService(SavedRepositoryRepository savedRepositoryRepository,
                                  UserService userService,
                                  RepositoryService repositoryService,
                                  RepositoryAnalysisRepository analysisRepository) {
        this.savedRepositoryRepository = savedRepositoryRepository;
        this.userService = userService;
        this.repositoryService = repositoryService;
        this.analysisRepository = analysisRepository;
    }

    @Transactional
    public boolean toggleBookmark(String firebaseUid, SavedRepositoryRequest request) {
        log.info("Toggling bookmark for user {} on repo {}/{}",
                firebaseUid, request.getOwner(), request.getRepositoryName());

        User user = userService.getOrCreateEntity(firebaseUid, null, null);
        Repository repository = repositoryService.findOrCreateRepository(
                request.getOwner(),
                request.getRepositoryName(),
                request.getRepositoryUrl(),
                request.getDescription(),
                request.getLanguage()
        );

        Optional<SavedRepository> existing = savedRepositoryRepository.findByUserAndRepository(user, repository);
        if (existing.isPresent()) {
            savedRepositoryRepository.delete(existing.get());
            log.info("Removed bookmark for {}/{}", request.getOwner(), request.getRepositoryName());
            return false;
        } else {
            SavedRepository saved = SavedRepository.builder()
                    .user(user)
                    .repository(repository)
                    .notes(request.getNotes())
                    .savedAt(LocalDateTime.now())
                    .build();
            savedRepositoryRepository.save(saved);
            log.info("Created bookmark for {}/{}", request.getOwner(), request.getRepositoryName());
            return true;
        }
    }

    @Transactional(readOnly = true)
    public List<SavedRepositoryResponse> getUserSavedRepositories(String firebaseUid) {
        List<SavedRepository> list = savedRepositoryRepository.findByUser_FirebaseUidOrderBySavedAtDesc(firebaseUid);

        return list.stream().map(saved -> {
            String status = "not_assessed";
            String lastAssessmentDate = null;
            if (saved.getRepository() != null) {
                Optional<RepositoryAnalysis> analysis = analysisRepository.findTopByRepositoryOrderByAnalyzedAtDesc(saved.getRepository());
                if (analysis.isPresent()) {
                    status = analysis.get().getAssessmentStatus();
                    if (analysis.get().getAnalyzedAt() != null) {
                        lastAssessmentDate = analysis.get().getAnalyzedAt().format(DateTimeFormatter.ISO_DATE_TIME);
                    }
                }
            }
            return SavedRepositoryResponse.fromEntity(saved, status, lastAssessmentDate);
        }).collect(Collectors.toList());
    }

    @Transactional
    public void deleteSavedRepository(String firebaseUid, Long id) {
        User user = userService.getOrCreateEntity(firebaseUid, null, null);
        int deleted = savedRepositoryRepository.deleteByUserAndId(user, id);
        if (deleted == 0) {
            throw new ResourceNotFoundException("Saved repository not found or does not belong to user");
        }
    }

    @Transactional(readOnly = true)
    public boolean isSaved(String firebaseUid, String owner, String name) {
        return savedRepositoryRepository.existsByFirebaseUidAndOwnerAndName(firebaseUid, owner.trim(), name.trim());
    }
}
