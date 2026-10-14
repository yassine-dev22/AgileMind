package com.ehtp.kanban_backend.dto;

import com.ehtp.kanban_backend.model.Task;
import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class TaskRequestDTO {
    @NotBlank @Size(max = 255)
    private String title;
    @Size(max = 2000)
    private String description;
    private Task.Priority priority;
    private Task.TaskStatus status;
    @Min(0) @Max(10000)
    private Integer effortPoints;
    @Min(0) @Max(100000)
    private Integer estimatedHours;
    @Positive private Long sprintId;
    @Positive private Long requirementId;
    @Positive private Long assigneeId;
}
