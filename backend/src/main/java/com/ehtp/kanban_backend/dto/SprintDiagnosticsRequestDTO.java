package com.ehtp.kanban_backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SprintDiagnosticsRequestDTO(
        @JsonAlias("sprint_id") @NotNull @Positive Long sprintId
) {}
