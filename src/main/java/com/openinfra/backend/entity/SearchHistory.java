package com.openinfra.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_history", indexes = {
    @Index(name = "idx_history_user_id", columnList = "user_id"),
    @Index(name = "idx_history_last_viewed", columnList = "last_viewed_at"),
    @Index(name = "idx_history_user_repo", columnList = "user_id, repository_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @Column(name = "search_query", length = 500)
    private String searchQuery;

    @Column(name = "status_snapshot", length = 50)
    private String statusSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_analysis_id")
    private RepositoryAnalysis latestAnalysis;

    @Column(name = "view_count")
    private Integer viewCount;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    @Column(name = "last_viewed_at", nullable = false)
    private LocalDateTime lastViewedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.searchedAt == null) {
            this.searchedAt = now;
        }
        if (this.lastViewedAt == null) {
            this.lastViewedAt = now;
        }
        if (this.viewCount == null) {
            this.viewCount = 1;
        }
    }
}
