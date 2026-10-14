package com.ehtp.kanban_backend.service;

import com.ehtp.kanban_backend.dto.*;
import com.ehtp.kanban_backend.model.*;
import com.ehtp.kanban_backend.repository.*;
import com.ehtp.kanban_backend.service.state.TaskStateFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class TaskService {
    private final TaskRepository taskRepository;
    private final SprintRepository sprintRepository;
    private final UserRepository userRepository;
    private final TaskStatusHistoryRepository historyRepository;
    private final AuthorizationService authorizationService;
    private final RequirementRepository requirementRepository;
    private final WebhookDispatcherService webhookDispatcherService;
    private final ProjectMutationGuard mutationGuard;

    public TaskService(TaskRepository taskRepository, SprintRepository sprintRepository,
                       UserRepository userRepository, TaskStatusHistoryRepository historyRepository,
                       AuthorizationService authorizationService, RequirementRepository requirementRepository,
                       WebhookDispatcherService webhookDispatcherService, ProjectMutationGuard mutationGuard) {
        this.taskRepository = taskRepository;
        this.sprintRepository = sprintRepository;
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
        this.authorizationService = authorizationService;
        this.requirementRepository = requirementRepository;
        this.webhookDispatcherService = webhookDispatcherService;
        this.mutationGuard = mutationGuard;
    }

    public Task createTask(TaskRequestDTO dto) {
        if (dto.getSprintId() == null || dto.getRequirementId() == null) {
            throw badRequest("sprintId and requirementId are required");
        }
        Long projectId = sprintRepository.findProjectIdBySprintId(dto.getSprintId())
                .orElseThrow(() -> notFound("Sprint"));
        authorizationService.requireProjectOwner(projectId);
        Projet project = mutationGuard.lock(projectId);
        Requirement requirement = requirement(dto.getRequirementId());
        validateAssociation(requirement, dto, project.getId());
        return create(requirement, dto, project);
    }

    public Task createSubtask(Long requirementId, TaskRequestDTO dto) {
        Long projectId = requirementRepository.findProjectIdByRequirementId(requirementId)
                .orElseThrow(() -> notFound("Requirement"));
        authorizationService.requireProjectOwner(projectId);
        Projet project = mutationGuard.lock(projectId);
        Requirement requirement = requirement(requirementId);
        if (dto.getRequirementId() != null && dto.getRequirementId().longValue() != requirementId.longValue()) {
            throw badRequest("requirementId must match the URL");
        }
        if (dto.getSprintId() != null && (requirement.getSprint() == null
                || requirement.getSprint().getSprintId() != dto.getSprintId().longValue())) {
            throw badRequest("sprintId must match the requirement's sprint");
        }
        return create(requirement, dto, project);
    }

    private Task create(Requirement requirement, TaskRequestDTO dto, Projet project) {
        mutationGuard.requireMutable(requirement.getSprint());
        Task task = new Task();
        applyEditableFields(task, dto, requirement, project);
        task.setStatus(dto.getStatus() == null ? Task.TaskStatus.TODO : dto.getStatus());
        task.setStatusChangedAt(LocalDateTime.now());
        Task saved = taskRepository.save(task);
        mutationGuard.updatePlanning(requirement);
        dispatch(saved.getSprint());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Task> getAllTasks() {
        return taskRepository.findVisibleToUser(authorizationService.currentUser().getId());
    }

    @Transactional(readOnly = true)
    public List<Task> getTasksBySprint(Long sprintId) {
        Long projectId = sprintRepository.findProjectIdBySprintId(sprintId)
                .orElseThrow(() -> notFound("Sprint"));
        authorizationService.requireProjectViewer(projectId);
        return taskRepository.findBySprintSprintId(sprintId);
    }

    @Transactional(readOnly = true)
    public Task getTaskById(Long id) {
        Task task = task(id);
        authorizationService.requireProjectViewer(task.getRequirement().getProject().getId());
        return task;
    }

    public Task updateTask(Long id, TaskRequestDTO dto) {
        Projet project = lockForTask(id, true);
        Task task = task(id);
        mutationGuard.requireMutable(task.getSprint());
        Requirement previousRequirement = task.getRequirement();
        Sprint previousSprint = task.getSprint();
        if (dto.getRequirementId() == null) throw badRequest("requirementId is required");
        Requirement requirement = requirement(dto.getRequirementId());
        validateAssociation(requirement, dto, project.getId());
        mutationGuard.requireMutable(requirement.getSprint());
        applyEditableFields(task, dto, requirement, project);
        if (dto.getStatus() != null) changeStatus(task, dto.getStatus(), authorizationService.currentUser());
        Task saved = taskRepository.save(task);
        mutationGuard.updatePlanning(previousRequirement);
        if (requirement.getId() != previousRequirement.getId()) mutationGuard.updatePlanning(requirement);
        dispatch(saved.getSprint());
        if (previousSprint != null && (saved.getSprint() == null
                || previousSprint.getSprintId() != saved.getSprint().getSprintId())) dispatch(previousSprint);
        return saved;
    }

    public Task updateStatus(Long id, TaskStatusUpdateDTO dto) {
        lockForTask(id, false);
        Task task = task(id);
        mutationGuard.requireMutable(task.getSprint());
        changeStatus(task, dto.getStatus(), authorizationService.currentUser());
        Task saved = taskRepository.save(task);
        dispatch(saved.getSprint());
        return saved;
    }

    public TaskAssignmentResponseDTO assignTask(Long id, TaskAssignmentDTO dto) {
        Projet project = lockForTask(id, false);
        Task task = task(id);
        mutationGuard.requireMutable(task.getSprint());
        if (dto.getAssigneeId() == null) throw badRequest("assigneeId is required");
        User assignee = assignee(dto.getAssigneeId(), project);
        task.setAssignee(assignee);
        Task saved = taskRepository.save(task);
        dispatch(saved.getSprint());
        int workload = saved.getSprint() == null ? 0 : taskRepository
                .findBySprintAndAssigneeIdAndStatusNot(saved.getSprint(), assignee.getId(), Task.TaskStatus.DONE)
                .stream().mapToInt(t -> t.getEffortPoints() == null ? 0 : t.getEffortPoints()).sum();
        int capacity = assignee.getCapacityPoints() == null ? 0 : assignee.getCapacityPoints();
        boolean overAssigned = workload > capacity;
        String warning = overAssigned ? "Warning: assignee workload is " + workload
                + " points, above capacity of " + capacity + " points." : null;
        return new TaskAssignmentResponseDTO(saved, overAssigned, workload, capacity, warning);
    }

    public void deleteTask(Long id) {
        lockForTask(id, true);
        Task task = task(id);
        mutationGuard.requireMutable(task.getSprint());
        Requirement requirement = task.getRequirement();
        Sprint sprint = task.getSprint();
        taskRepository.delete(task);
        mutationGuard.updatePlanning(requirement);
        dispatch(sprint);
    }

    private Projet lockForTask(Long id, boolean ownerOnly) {
        Long projectId = taskRepository.findProjectIdByTaskId(id).orElseThrow(() -> notFound("Task"));
        if (ownerOnly) authorizationService.requireProjectOwner(projectId);
        else authorizationService.requireProjectEditor(projectId);
        return mutationGuard.lock(projectId);
    }

    private void validateAssociation(Requirement requirement, TaskRequestDTO dto, long projectId) {
        if (requirement.getProject().getId() != projectId) throw badRequest("Requirement belongs to another project");
        Long actualSprintId = requirement.getSprint() == null ? null : requirement.getSprint().getSprintId();
        if (!java.util.Objects.equals(actualSprintId, dto.getSprintId())) {
            throw badRequest("sprintId must match the requirement's sprint");
        }
    }

    private void applyEditableFields(Task task, TaskRequestDTO dto, Requirement requirement, Projet project) {
        if (dto.getTitle() == null || dto.getTitle().isBlank() || dto.getTitle().length() > 255) {
            throw badRequest("title must contain between 1 and 255 characters");
        }
        if (dto.getDescription() != null && dto.getDescription().length() > 2000) throw badRequest("description exceeds 2000 characters");
        if (dto.getEffortPoints() != null && (dto.getEffortPoints() < 0 || dto.getEffortPoints() > 10000)) throw badRequest("effortPoints must be between 0 and 10000");
        if (dto.getEstimatedHours() != null && (dto.getEstimatedHours() < 0 || dto.getEstimatedHours() > 100000)) throw badRequest("estimatedHours must be between 0 and 100000");
        task.setTitle(dto.getTitle().trim());
        task.setDescription(dto.getDescription() == null ? "" : dto.getDescription());
        task.setPriority(dto.getPriority() == null ? Task.Priority.MEDIUM : dto.getPriority());
        task.setEffortPoints(dto.getEffortPoints() == null ? 0 : dto.getEffortPoints());
        task.setEstimatedHours(dto.getEstimatedHours() == null ? 0 : dto.getEstimatedHours());
        task.setRequirement(requirement);
        task.setSprint(requirement.getSprint());
        task.setAssignee(dto.getAssigneeId() == null ? null : assignee(dto.getAssigneeId(), project));
    }

    private User assignee(Long id, Projet project) {
        boolean member = project.getOwner().getId() == id.longValue()
                || project.getMembers().stream().anyMatch(user -> user.getId() == id.longValue());
        if (!member) throw badRequest("Assignee must be a member of the project");
        User user = userRepository.findById(id).orElseThrow(() -> notFound("User"));
        if (user.getRole() == User.Role.VIEWER) throw badRequest("A viewer cannot be assigned tasks");
        return user;
    }

    private void changeStatus(Task task, Task.TaskStatus to, User changedBy) {
        if (to == null) throw badRequest("Target status is required");
        Task.TaskStatus from = task.getStatus();
        if (from == to) return;
        TaskStateFactory.getState(from).transitionTo(task, to);
        LocalDateTime changedAt = LocalDateTime.now();
        task.setStatus(to);
        task.setStatusChangedAt(changedAt);
        TaskStatusHistory history = new TaskStatusHistory();
        history.setTask(task);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setChangedAt(changedAt);
        history.setChangedBy(changedBy);
        historyRepository.save(history);
    }

    private Task task(Long id) { return taskRepository.findById(id).orElseThrow(() -> notFound("Task")); }
    private Requirement requirement(Long id) { return requirementRepository.findById(id).orElseThrow(() -> notFound("Requirement")); }
    private void dispatch(Sprint sprint) { if (sprint != null) webhookDispatcherService.dispatchSprintMetrics(sprint); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String resource) { return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " not found"); }
}
