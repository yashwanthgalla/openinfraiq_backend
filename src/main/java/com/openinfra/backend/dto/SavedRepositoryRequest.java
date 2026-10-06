package com.openinfra.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SavedRepositoryRequest {

    @NotBlank(message = "Owner is required")
    private String owner;

    @NotBlank(message = "Repository name is required")
    private String repositoryName;

    private String repositoryUrl;
    private String status;
    private String description;
    private String language;
    private String notes;

    @com.fasterxml.jackson.annotation.JsonProperty("name")
    public void setName(String name) {
        if (this.repositoryName == null || this.repositoryName.isBlank()) {
            this.repositoryName = name;
        }
    }

    @com.fasterxml.jackson.annotation.JsonProperty("url")
    public void setUrl(String url) {
        if (this.repositoryUrl == null || this.repositoryUrl.isBlank()) {
            this.repositoryUrl = url;
        }
    }
}
