package com.ehtp.kanban_backend.dto;

import com.ehtp.kanban_backend.model.Task;
import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class TaskStatusUpdateDTO {
    @NotNull private Task.TaskStatus status;
}
