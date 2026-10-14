package com.ehtp.kanban_backend.controller;

import com.ehtp.kanban_backend.dto.TaskAssignmentDTO;
import com.ehtp.kanban_backend.dto.TaskAssignmentResponseDTO;
import com.ehtp.kanban_backend.dto.TaskRequestDTO;
import com.ehtp.kanban_backend.dto.TaskStatusUpdateDTO;
import com.ehtp.kanban_backend.model.Task;
import com.ehtp.kanban_backend.service.TaskService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public Task createTask(@Valid @RequestBody TaskRequestDTO dto) {
        return taskService.createTask(dto);
    }

    @GetMapping
    public List<Task> getAllTasks(@RequestParam(required = false) Long sprintId) {
        if (sprintId != null) {
            return taskService.getTasksBySprint(sprintId);
        }
        return taskService.getAllTasks();
    }

    @GetMapping("/{id}")
    public Task getTaskById(@PathVariable Long id) {
        return taskService.getTaskById(id);
    }

    @PutMapping("/{id}")
    public Task updateTask(@PathVariable Long id, @Valid @RequestBody TaskRequestDTO dto) {
        return taskService.updateTask(id, dto);
    }

    @PatchMapping("/{id}/status")
    public Task updateTaskStatus(@PathVariable Long id, @Valid @RequestBody TaskStatusUpdateDTO dto) {
        return taskService.updateStatus(id, dto);
    }

    @PostMapping("/{id}/assign")
    public TaskAssignmentResponseDTO assignTask(@PathVariable Long id, @Valid @RequestBody TaskAssignmentDTO dto) {
        return taskService.assignTask(id, dto);
    }

    @DeleteMapping("/{id}")
    public void deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
    }
}
