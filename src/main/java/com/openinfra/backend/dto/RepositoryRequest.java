package com.openinfra.backend.dto;

import com.openinfra.backend.entity.Repository;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryRequest {

    @NotBlank(message = "Owner is required")
    private String owner;

    @NotBlank(message = "Repository name is required")
    private String name;

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
}
