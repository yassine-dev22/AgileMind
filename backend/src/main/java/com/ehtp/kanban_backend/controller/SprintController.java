package com.ehtp.kanban_backend.controller;

import com.ehtp.kanban_backend.dto.SprintRequestDTO;
import com.ehtp.kanban_backend.model.Sprint;
import com.ehtp.kanban_backend.service.SprintService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/sprints")
public class SprintController {

    private final SprintService sprintService;

    public SprintController(SprintService sprintService) {
        this.sprintService = sprintService;
    }

    @PostMapping
    public Sprint createSprint(@Valid @RequestBody SprintRequestDTO dto) {
        return sprintService.createSprint(dto);
    }

    @GetMapping
    public List<Sprint> getAllSprints() {
        return sprintService.getAllSprints();
    }

    @GetMapping("/{id}")
    public Sprint getSprintById(@PathVariable Long id) {
        return sprintService.getSprintById(id);
    }

    @GetMapping("/project/{projectId}")
    public List<Sprint> getSprintsByProject(@PathVariable Long projectId) {
        return sprintService.getSprintsByProject(projectId);
    }

    @PutMapping("/{id}")
    public Sprint updateSprint(@PathVariable Long id, @Valid @RequestBody SprintRequestDTO dto) {
        return sprintService.updateSprint(id, dto);
    }

    @DeleteMapping("/{id}")
    public void deleteSprint(@PathVariable Long id) {
        sprintService.deleteSprint(id);
    }

    @PatchMapping("/{id}/complete")
    public Sprint completeSprint(@PathVariable Long id) {
        return sprintService.completeSprint(id);
    }
}
