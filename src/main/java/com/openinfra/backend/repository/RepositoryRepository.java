package com.openinfra.backend.repository;

import com.openinfra.backend.entity.Repository;
import com.openinfra.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Repository
public interface RepositoryRepository extends JpaRepository<Repository, Long> {

    // Ownership queries strictly scoped to authenticated user
    Optional<Repository> findByUserAndId(User user, Long id);

    Optional<Repository> findByUserAndFullNameIgnoreCase(User user, String fullName);

    Optional<Repository> findByUserAndOwnerIgnoreCaseAndNameIgnoreCase(User user, String owner, String name);

    List<Repository> findByUserOrderByUpdatedAtDesc(User user);

    Page<Repository> findByUserOrderByUpdatedAtDesc(User user, Pageable pageable);

    boolean existsByUserAndFullNameIgnoreCase(User user, String fullName);

    boolean existsByUserAndGithubRepositoryId(User user, Long githubRepositoryId);

    @Modifying
    @Query("DELETE FROM Repository r WHERE r.user = :user AND r.id = :id")
    int deleteByUserAndId(@Param("user") User user, @Param("id") Long id);

    // Global queries (e.g. system cache / shared repositories)
    Optional<Repository> findFirstByFullNameIgnoreCaseOrderByUpdatedAtDesc(String fullName);

    Optional<Repository> findFirstByOwnerIgnoreCaseAndNameIgnoreCaseOrderByUpdatedAtDesc(String owner, String name);
}
