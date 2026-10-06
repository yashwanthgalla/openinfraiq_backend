package com.openinfra.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIRequirementRequest {

    @NotBlank(message = "Requirement description is required")
    @Size(min = 3, max = 2000, message = "Description must be between 3 and 2000 characters")
    private String description;

    private String title;
}
