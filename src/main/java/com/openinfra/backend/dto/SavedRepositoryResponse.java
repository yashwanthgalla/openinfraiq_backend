package com.openinfra.backend.dto;

import com.openinfra.backend.entity.SavedRepository;
import lombok.*;

import java.time.format.DateTimeFormatter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedRepositoryResponse {

    private String id;
    private String repositoryUrl;
    private String owner;
    private String repositoryName;
    private String savedAt;
    private String lastAssessmentDate;
    private String status;
    private String language;
    private String description;
    private Integer starsCount;
    private Integer forksCount;
    private Integer openIssuesCount;

    public static SavedRepositoryResponse fromEntity(SavedRepository saved, String status, String lastAssessmentDate) {
        if (saved == null) return null;

        String repoUrl = saved.getRepository() != null ? saved.getRepository().getUrl() : null;
        String owner = saved.getRepository() != null ? saved.getRepository().getOwner() : null;
        String repoName = saved.getRepository() != null ? saved.getRepository().getName() : null;
        String language = saved.getRepository() != null ? saved.getRepository().getPrimaryLanguage() : null;
        String description = saved.getRepository() != null ? saved.getRepository().getDescription() : null;
        Integer stars = saved.getRepository() != null ? saved.getRepository().getStarsCount() : null;
        Integer forks = saved.getRepository() != null ? saved.getRepository().getForksCount() : null;
        Integer openIssues = saved.getRepository() != null ? saved.getRepository().getOpenIssuesCount() : null;

        return SavedRepositoryResponse.builder()
                .id(String.valueOf(saved.getId()))
                .repositoryUrl(repoUrl)
                .owner(owner)
                .repositoryName(repoName)
                .savedAt(saved.getSavedAt() != null ? saved.getSavedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null)
                .lastAssessmentDate(lastAssessmentDate)
                .status(status != null ? status : "not_assessed")
                .language(language)
                .description(description)
                .starsCount(stars)
                .forksCount(forks)
                .openIssuesCount(openIssues)
                .build();
    }
}
