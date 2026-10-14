package com.ehtp.kanban_backend.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Data
public class AiCallbackRequestDTO {
    @NotNull @Positive
    private Long sprintId;
    @PositiveOrZero
    private Long analysisRevision;
    @Size(max = 4000)
    private String predictiveRisk;
    @Size(max = 4000)
    private String reassignmentSuggestions;
    @Size(max = 4000)
    private String historicalPatterns;
}
