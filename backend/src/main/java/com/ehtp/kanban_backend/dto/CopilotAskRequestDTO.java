package com.ehtp.kanban_backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CopilotAskRequestDTO(
        @NotBlank @Size(max = 500) String question,
        @JsonProperty("project_id") @JsonAlias("projectId") @NotNull @Positive Long projectId
) {}
