package com.openinfra.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalyzeRepoRequest {
    private String owner;
    private String name;
    private String repositoryUrl;
    private String url;
}
