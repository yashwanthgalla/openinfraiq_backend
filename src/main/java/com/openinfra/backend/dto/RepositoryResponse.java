package com.openinfra.backend.dto;

import com.openinfra.backend.entity.Repository;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryResponse {

    private Long id;
    private String owner;
    private String name;
    private String fullName;
    private String url;
    private String description;
    private String primaryLanguage;
    private String defaultBranch;
    private Integer starsCount;
    private Integer forksCount;
    private Integer openIssuesCount;
    private Integer watchersCount;
    private Boolean isArchived;
    private String licenseName;
    private String licenseSpdxId;
    private LocalDateTime lastActivityAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RepositoryResponse fromEntity(Repository repo) {
        if (repo == null) return null;
        return RepositoryResponse.builder()
                .id(repo.getId())
                .owner(repo.getOwner())
                .name(repo.getName())
                .fullName(repo.getFullName())
                .url(repo.getUrl())
                .description(repo.getDescription())
                .primaryLanguage(repo.getPrimaryLanguage())
                .defaultBranch(repo.getDefaultBranch())
                .starsCount(repo.getStarsCount())
                .forksCount(repo.getForksCount())
                .openIssuesCount(repo.getOpenIssuesCount())
                .watchersCount(repo.getWatchersCount())
                .isArchived(repo.getIsArchived())
                .licenseName(repo.getLicenseName())
                .licenseSpdxId(repo.getLicenseSpdxId())
                .lastActivityAt(repo.getLastActivityAt())
                .createdAt(repo.getCreatedAt())
                .updatedAt(repo.getUpdatedAt())
                .build();
    }
}
