package com.openinfra.backend.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openinfra.backend.entity.SearchHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.format.DateTimeFormatter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchHistoryResponse {

    private String id;
    private String repositoryUrl;
    private String owner;
    private String repositoryName;
    private String searchedAt;
    private String lastViewedAt;
    private String status;
    private String language;
    private String description;
    private Object assessmentData;

    public static SearchHistoryResponse fromEntity(SearchHistory history, ObjectMapper objectMapper) {
        if (history == null) return null;

        Object parsedAssessment = null;
        if (history.getLatestAnalysis() != null && history.getLatestAnalysis().getRawAnalysisJson() != null) {
            try {
                parsedAssessment = objectMapper.readValue(history.getLatestAnalysis().getRawAnalysisJson(), Object.class);
            } catch (Exception ignored) {
            }
        }

        String repoUrl = history.getRepository() != null ? history.getRepository().getUrl() : null;
        String owner = history.getRepository() != null ? history.getRepository().getOwner() : null;
        String repoName = history.getRepository() != null ? history.getRepository().getName() : null;
        String language = history.getRepository() != null ? history.getRepository().getPrimaryLanguage() : null;
        String description = history.getRepository() != null ? history.getRepository().getDescription() : null;

        return SearchHistoryResponse.builder()
                .id(String.valueOf(history.getId()))
                .repositoryUrl(repoUrl)
                .owner(owner)
                .repositoryName(repoName)
                .searchedAt(history.getSearchedAt() != null ? history.getSearchedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null)
                .lastViewedAt(history.getLastViewedAt() != null ? history.getLastViewedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null)
                .status(history.getStatusSnapshot() != null ? history.getStatusSnapshot() : "not_assessed")
                .language(language)
                .description(description)
                .assessmentData(parsedAssessment)
                .build();
    }
}
