package com.ehtp.kanban_backend.controller;

import com.ehtp.kanban_backend.dto.RequirementRequestDTO;
import com.ehtp.kanban_backend.dto.TaskRequestDTO;
import com.ehtp.kanban_backend.model.Requirement;
import com.ehtp.kanban_backend.model.Task;
import com.ehtp.kanban_backend.service.RequirementService;
import com.ehtp.kanban_backend.service.TaskService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/requirements")
public class RequirementController {
    private final RequirementService requirementService;
    private final TaskService taskService;

    public RequirementController(RequirementService requirementService, TaskService taskService) {
        this.requirementService = requirementService;
        this.taskService = taskService;
    }

    @PostMapping
    public Requirement createRequirement(@Valid @RequestBody RequirementRequestDTO dto) {
        return requirementService.createRequirement(dto);
    }

    @GetMapping("/sprint/{sprintId}")
    public List<Requirement> getRequirementsBySprint(@PathVariable Long sprintId) {
        return requirementService.getRequirementsBySprint(sprintId);
    }

    @GetMapping("/{id}")
    public Requirement getRequirementById(@PathVariable Long id) {
        return requirementService.getRequirementById(id);
    }

    @PutMapping("/{id}")
    public Requirement updateRequirement(@PathVariable Long id, @Valid @RequestBody RequirementRequestDTO dto) {
        return requirementService.updateRequirement(id, dto);
    }

    @DeleteMapping("/{id}")
    public void deleteRequirement(@PathVariable Long id) {
        requirementService.deleteRequirement(id);
    }

    @PostMapping("/{requirementId}/subtasks")
    public Task createSubtask(@PathVariable Long requirementId, @Valid @RequestBody TaskRequestDTO dto) {
        return taskService.createSubtask(requirementId, dto);
    }
}
