package com.openinfra.backend.repository;

import com.openinfra.backend.entity.Repository;
import com.openinfra.backend.entity.RepositoryAnalysis;
import com.openinfra.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Repository
public interface RepositoryAnalysisRepository extends JpaRepository<RepositoryAnalysis, Long> {

    // Ownership-enforced analysis retrieval
    Page<RepositoryAnalysis> findByRepository_UserAndRepository_IdOrderByAnalyzedAtDesc(
            User user, Long repositoryId, Pageable pageable
    );

    List<RepositoryAnalysis> findByRepository_UserAndRepository_IdOrderByAnalyzedAtDesc(
            User user, Long repositoryId
    );

    Optional<RepositoryAnalysis> findTopByRepository_UserAndRepository_IdOrderByAnalyzedAtDesc(
            User user, Long repositoryId
    );

    Page<RepositoryAnalysis> findByRepository_UserOrderByAnalyzedAtDesc(User user, Pageable pageable);

    @Query("SELECT ra FROM RepositoryAnalysis ra " +
           "WHERE ra.repository.user = :user " +
           "AND LOWER(ra.repository.owner) = LOWER(:owner) " +
           "AND LOWER(ra.repository.name) = LOWER(:name) " +
           "ORDER BY ra.analyzedAt DESC LIMIT 1")
    Optional<RepositoryAnalysis> findLatestByUserAndOwnerAndName(
            @Param("user") User user, @Param("owner") String owner, @Param("name") String name
    );

    // Single repository lookup
    Page<RepositoryAnalysis> findByRepositoryOrderByAnalyzedAtDesc(Repository repository, Pageable pageable);

    Optional<RepositoryAnalysis> findTopByRepositoryOrderByAnalyzedAtDesc(Repository repository);

    @Query("SELECT ra FROM RepositoryAnalysis ra " +
           "WHERE LOWER(ra.repository.owner) = LOWER(:owner) " +
           "AND LOWER(ra.repository.name) = LOWER(:name) " +
           "ORDER BY ra.analyzedAt DESC LIMIT 1")
    Optional<RepositoryAnalysis> findLatestByOwnerAndName(@Param("owner") String owner, @Param("name") String name);
}
