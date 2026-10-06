package com.openinfra.backend.repository;

import com.openinfra.backend.entity.ProjectRequirement;
import com.openinfra.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRequirementRepository extends JpaRepository<ProjectRequirement, Long> {
    List<ProjectRequirement> findByUserOrderByCreatedAtDesc(User user);
    Optional<ProjectRequirement> findByIdAndUser(Long id, User user);
    void deleteByIdAndUser(Long id, User user);
}
