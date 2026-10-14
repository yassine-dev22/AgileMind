package com.ehtp.kanban_backend.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
public class TaskAssignmentDTO {
    @NotNull @Positive private Long assigneeId;
}
