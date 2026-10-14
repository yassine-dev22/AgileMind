package com.ehtp.kanban_backend.service;

import com.ehtp.kanban_backend.dto.SprintRequestDTO;
import com.ehtp.kanban_backend.exception.ActiveSprintExistsException;
import com.ehtp.kanban_backend.model.*;
import com.ehtp.kanban_backend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class SprintService {
    private final SprintRepository sprintRepository;
    private final RequirementRepository requirementRepository;
    private final AuthorizationService authorizationService;
    private final WebhookDispatcherService webhookDispatcherService;
    private final ProjectMutationGuard mutationGuard;
    private final TaskRepository taskRepository;

    public SprintService(SprintRepository sprintRepository, RequirementRepository requirementRepository,
                         AuthorizationService authorizationService, WebhookDispatcherService webhookDispatcherService,
                         ProjectMutationGuard mutationGuard, TaskRepository taskRepository) {
        this.sprintRepository = sprintRepository;
        this.requirementRepository = requirementRepository;
        this.authorizationService = authorizationService;
        this.webhookDispatcherService = webhookDispatcherService;
        this.mutationGuard = mutationGuard;
        this.taskRepository = taskRepository;
    }

    public Sprint createSprintForProject(Long projectId, SprintRequestDTO dto) {
        dto.setProjectId(projectId);
        return createSprint(dto);
    }

    public Sprint createSprint(SprintRequestDTO dto) {
        validateSprintRequest(dto);
        if (dto.getProjectId() == null) throw badRequest("projectId is required");
        authorizationService.requireProjectOwner(dto.getProjectId());
        Projet project = mutationGuard.lock(dto.getProjectId());
        Sprint.SprintStatus status = dto.getSprintStatus() == null ? Sprint.SprintStatus.PLANNED : dto.getSprintStatus();
        if (status == Sprint.SprintStatus.DONE) throw badRequest("A sprint cannot be created completed");
        if (status == Sprint.SprintStatus.ACTIVE) ensureNoOtherActive(project.getId(), 0);
        Sprint sprint = new Sprint();
        sprint.setProject(project);
        applyFields(sprint, dto);
        sprint.setSprintStatus(status);
        Sprint saved = sprintRepository.save(sprint);
        reconcileScope(saved, dto.getRequirementIds(), false);
        // Freeze the persisted scope's effort as the activation baseline, never a client total.
        saved.setPointsPlanned(Math.toIntExact(taskRepository.sumEffortBySprintId(saved.getSprintId())));
        webhookDispatcherService.dispatchSprintMetrics(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Sprint> getAllSprints() {
        return sprintRepository.findVisibleToUser(authorizationService.currentUser().getId());
    }

    @Transactional(readOnly = true)
    public List<Sprint> getSprintsByProject(Long projectId) {
        authorizationService.requireProjectViewer(projectId);
        return sprintRepository.findByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public Sprint getSprintById(Long id) {
        Sprint sprint = sprint(id);
        authorizationService.requireProjectViewer(sprint.getProject().getId());
        return sprint;
    }

    public Sprint updateSprint(Long id, SprintRequestDTO dto) {
        validateSprintRequest(dto);
        lockForSprint(id, true);
        Sprint sprint = sprint(id);
        mutationGuard.requireMutable(sprint);
        if (dto.getProjectId() != null && dto.getProjectId().longValue() != sprint.getProject().getId()) {
            throw badRequest("A sprint cannot be moved to another project");
        }
        Sprint.SprintStatus previous = sprint.getSprintStatus();
        Sprint.SprintStatus target = dto.getSprintStatus() == null ? previous : dto.getSprintStatus();
        if (previous == Sprint.SprintStatus.ACTIVE && target == Sprint.SprintStatus.PLANNED) {
            throw badRequest("An active sprint cannot return to PLANNED");
        }
        // Check/reconcile scope before completing so PUT cannot bypass the completion invariant.
        reconcileScope(sprint, dto.getRequirementIds(), previous == Sprint.SprintStatus.ACTIVE);
        if (target == Sprint.SprintStatus.DONE) validateCompletion(sprint);
        if (target == Sprint.SprintStatus.ACTIVE) ensureNoOtherActive(sprint.getProject().getId(), id);
        if (previous == Sprint.SprintStatus.ACTIVE && dto.getPointsPlanned() != null
                && !dto.getPointsPlanned().equals(sprint.getPointsPlanned())) {
            throw badRequest("Planned points are frozen after sprint activation");
        }
        applyFields(sprint, dto);
        if (previous == Sprint.SprintStatus.PLANNED) mutationGuard.updatePlanning(sprint);
        sprint.setSprintStatus(target);
        if (target == Sprint.SprintStatus.ACTIVE && previous == Sprint.SprintStatus.PLANNED) {
            for (Requirement requirement : requirementRepository.findBySprintSprintId(id)) {
                if (requirement.getStatus() == Requirement.RequirementStatus.BACKLOG) {
                    requirement.setStatus(Requirement.RequirementStatus.TODO);
                }
            }
        }
        mutationGuard.updatePlanning(sprint);
        Sprint saved = sprintRepository.save(sprint);
        webhookDispatcherService.dispatchSprintMetrics(saved);
        return saved;
    }

    public void deleteSprint(Long id) {
        lockForSprint(id, true);
        Sprint sprint = sprint(id);
        if (sprint.getSprintStatus() != Sprint.SprintStatus.PLANNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only planned sprints can be deleted");
        }
        // Requirements own their tasks; deleting a planning container must retain the backlog.
        for (Requirement requirement : requirementRepository.findBySprintSprintId(id)) detach(requirement);
        sprintRepository.delete(sprint);
    }

    public Sprint completeSprint(Long id) {
        lockForSprint(id, false);
        Sprint sprint = sprint(id);
        validateCompletion(sprint);
        sprint.setSprintStatus(Sprint.SprintStatus.DONE);
        Sprint saved = sprintRepository.save(sprint);
        webhookDispatcherService.dispatchSprintMetrics(saved);
        return saved;
    }

    private void validateCompletion(Sprint sprint) {
        if (sprint.getSprintStatus() != Sprint.SprintStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an active sprint can be completed");
        }
        List<Task> tasks = taskRepository.findBySprintSprintId(sprint.getSprintId());
        if (tasks.isEmpty() || tasks.stream().anyMatch(task -> task.getStatus() != Task.TaskStatus.DONE)) {
            throw badRequest("Cannot complete sprint: it must contain subtasks and all must be DONE");
        }
    }

    private void reconcileScope(Sprint sprint, List<Long> ids, boolean frozen) {
        if (ids == null) return;
        Set<Long> selected = new HashSet<>(ids);
        if (selected.size() != ids.size() || selected.contains(null)) throw badRequest("requirementIds must be unique and non-null");
        List<Requirement> existing = requirementRepository.findBySprintSprintId(sprint.getSprintId());
        Set<Long> current = new HashSet<>();
        existing.forEach(requirement -> current.add(requirement.getId()));
        if (frozen && !selected.equals(current)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Active sprint scope cannot be changed");
        }
        List<Requirement> requirements = ids.isEmpty() ? List.of() : requirementRepository.findAllByIdIn(ids);
        if (requirements.size() != ids.size()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more requirements were not found");
        for (Requirement requirement : requirements) {
            if (requirement.getProject().getId() != sprint.getProject().getId()) throw badRequest("Requirement belongs to another project");
            if (requirement.getSprint() != null && requirement.getSprint().getSprintId() != sprint.getSprintId()) {
                throw badRequest("Requirement is already assigned to another sprint");
            }
        }
        existing.stream().filter(requirement -> !selected.contains(requirement.getId())).forEach(this::detach);
        for (Requirement requirement : requirements) {
            requirement.setSprint(sprint);
            // Preserve completed work and its timestamps/history; attach tasks even while PLANNED.
            requirement.getSubtasks().forEach(task -> task.setSprint(sprint));
            if (sprint.getSprintStatus() == Sprint.SprintStatus.ACTIVE && requirement.getStatus() == Requirement.RequirementStatus.BACKLOG) {
                requirement.setStatus(Requirement.RequirementStatus.TODO);
            }
        }
        sprint.setRequirements(new java.util.ArrayList<>(requirements));
        sprint.setTasks(taskRepository.findBySprintSprintId(sprint.getSprintId()));
    }

    private void detach(Requirement requirement) {
        requirement.setSprint(null);
        if (requirement.getStatus() == Requirement.RequirementStatus.TODO) requirement.setStatus(Requirement.RequirementStatus.BACKLOG);
        requirement.getSubtasks().forEach(task -> task.setSprint(null));
    }

    private void ensureNoOtherActive(Long projectId, long sprintId) {
        sprintRepository.findByProjectIdAndIsActive(projectId, true).ifPresent(active -> {
            if (active.getSprintId() != sprintId) throw new ActiveSprintExistsException("This project already has an active sprint");
        });
    }

    private void lockForSprint(Long id, boolean ownerOnly) {
        Long projectId = sprintRepository.findProjectIdBySprintId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));
        if (ownerOnly) authorizationService.requireProjectOwner(projectId);
        else authorizationService.requireProjectEditor(projectId);
        mutationGuard.lock(projectId);
    }

    private void applyFields(Sprint sprint, SprintRequestDTO dto) {
        sprint.setObjective(dto.getObjective().trim());
        sprint.setStartDate(dto.getStartDate());
        sprint.setEndDate(dto.getEndDate());
        if (sprint.getSprintStatus() == Sprint.SprintStatus.PLANNED) sprint.setPointsPlanned(dto.getPointsPlanned());
    }

    private Sprint sprint(Long id) {
        return sprintRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));
    }

    private void validateSprintRequest(SprintRequestDTO dto) {
        if (dto.getObjective() == null || dto.getObjective().isBlank() || dto.getObjective().length() > 10000) throw badRequest("objective must contain between 1 and 10000 characters");
        if (dto.getStartDate() == null || dto.getEndDate() == null || !dto.getStartDate().isBefore(dto.getEndDate())) throw badRequest("startDate must be before endDate");
        if (java.time.temporal.ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) > 366) throw badRequest("Sprint duration cannot exceed 366 days");
        if (dto.getPointsPlanned() == null || dto.getPointsPlanned() < 0) throw badRequest("pointsPlanned must be greater than or equal to 0");
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
