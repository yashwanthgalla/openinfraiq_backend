package com.openinfra.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "repositories",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_repo_fullname", columnNames = {"user_id", "full_name"})
    },
    indexes = {
        @Index(name = "idx_repo_user_id", columnList = "user_id"),
        @Index(name = "idx_repo_full_name", columnList = "full_name"),
        @Index(name = "idx_repo_owner_name", columnList = "owner, name")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Repository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "github_repository_id")
    private Long githubRepositoryId;

    @Column(nullable = false, length = 100)
    private String owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "html_url", length = 500)
    private String htmlUrl;

    @Column(name = "clone_url", length = 500)
    private String cloneUrl;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "primary_language", length = 100)
    private String primaryLanguage;

    @Column(name = "default_branch", length = 100)
    private String defaultBranch;

    @Column(name = "stars_count")
    private Integer starsCount;

    @Column(name = "forks_count")
    private Integer forksCount;

    @Column(name = "open_issues_count")
    private Integer openIssuesCount;

    @Column(name = "watchers_count")
    private Integer watchersCount;

    @Column(name = "is_private")
    private Boolean isPrivate;

    @Column(name = "is_archived")
    private Boolean isArchived;

    @Column(name = "license_name", length = 150)
    private String licenseName;

    @Column(name = "license_spdx_id", length = 50)
    private String licenseSpdxId;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.fullName == null && this.owner != null && this.name != null) {
            this.fullName = this.owner.toLowerCase() + "/" + this.name.toLowerCase();
        }
        if (this.htmlUrl == null && this.url != null) {
            this.htmlUrl = this.url;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
