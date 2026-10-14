package com.ehtp.kanban_backend.service;

import com.ehtp.kanban_backend.model.Projet;
import com.ehtp.kanban_backend.model.Requirement;
import com.ehtp.kanban_backend.model.Sprint;
import com.ehtp.kanban_backend.repository.ProjetRepository;
import com.ehtp.kanban_backend.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Serializes scope/status changes with sprint activation and completion for one project. */
@Component
public class ProjectMutationGuard {
    private final ProjetRepository projectRepository;
    private final TaskRepository taskRepository;

    public ProjectMutationGuard(ProjetRepository projectRepository, TaskRepository taskRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
    }

    public Projet lock(Long projectId) {
        return projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    public void requireMutable(Sprint sprint) {
        if (sprint != null && sprint.getSprintStatus() == Sprint.SprintStatus.DONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completed sprint is immutable");
        }
    }

    public void updatePlanning(Requirement requirement) {
        requirement.setStoryPoints(asPoints(taskRepository.sumEffortByRequirementId(requirement.getId())));
        updatePlanning(requirement.getSprint());
    }

    public void updatePlanning(Sprint sprint) {
        if (sprint != null && sprint.getSprintStatus() == Sprint.SprintStatus.PLANNED) {
            sprint.setPointsPlanned(asPoints(taskRepository.sumEffortBySprintId(sprint.getSprintId())));
        }
    }

    private int asPoints(Long points) {
        if (points != null && points > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total effort points exceed supported range");
        }
        return points == null ? 0 : points.intValue();
    }
}
