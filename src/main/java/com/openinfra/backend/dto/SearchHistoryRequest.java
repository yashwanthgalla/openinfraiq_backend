package com.openinfra.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchHistoryRequest {

    @NotBlank(message = "Owner is required")
    private String owner;

    @NotBlank(message = "Repository name is required")
    private String repositoryName;

    private String repositoryUrl;
    private String status;
    private String description;
    private String language;
    private JsonNode assessmentData;
}
