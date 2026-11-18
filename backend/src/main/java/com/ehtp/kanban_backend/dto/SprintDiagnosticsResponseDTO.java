package com.ehtp.kanban_backend.dto;

public record SprintDiagnosticsResponseDTO(
        Long sprintId,
        Integer healthScore,
        Long analysisRevision,
        String status,
        String message
) {}
