package com.ehtp.kanban_backend.service;

import com.ehtp.kanban_backend.dto.BottleneckDTO;
import com.ehtp.kanban_backend.dto.CapacityDTO;
import com.ehtp.kanban_backend.dto.HealthDTO;
import com.ehtp.kanban_backend.dto.VelocityDTO;
import com.ehtp.kanban_backend.dto.VelocityTrendDTO;
import com.ehtp.kanban_backend.dto.WorkloadDTO;
import com.ehtp.kanban_backend.model.Projet;
import com.ehtp.kanban_backend.model.Sprint;
import com.ehtp.kanban_backend.model.Task;
import com.ehtp.kanban_backend.model.TaskStatusHistory;
import com.ehtp.kanban_backend.model.User;
import com.ehtp.kanban_backend.repository.ProjetRepository;
import com.ehtp.kanban_backend.repository.SprintRepository;
import com.ehtp.kanban_backend.repository.TaskRepository;
import com.ehtp.kanban_backend.repository.TaskStatusHistoryRepository;
import org.springframework.data.domain.PageRequest;
import com.ehtp.kanban_backend.service.factory.MetricCalculatorFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MetricsService {
    private final SprintRepository sprintRepository;
    private final ProjetRepository projetRepository;
    private final TaskRepository taskRepository;
    private final TaskStatusHistoryRepository historyRepository;
    private final AuthorizationService authorizationService;
    private final MetricFormulaCalculator calculator;
    private final int reviewThresholdHours;

    public MetricsService(
            SprintRepository sprintRepository,
            ProjetRepository projetRepository,
            TaskRepository taskRepository,
            TaskStatusHistoryRepository historyRepository,
            AuthorizationService authorizationService,
            MetricFormulaCalculator calculator,
            @Value("${app.metrics.review-threshold-hours:24}") int reviewThresholdHours
    ) {
        this.sprintRepository = sprintRepository;
        this.projetRepository = projetRepository;
        this.taskRepository = taskRepository;
        this.historyRepository = historyRepository;
        this.authorizationService = authorizationService;
        this.calculator = calculator;
        this.reviewThresholdHours = reviewThresholdHours;
    }

    public CapacityDTO getCapacity(Long sprintId) {
        Sprint sprint = getSprint(sprintId);
        int planned = sprint.getPointsPlanned() == null ? 0 : sprint.getPointsPlanned();

        List<Task> tasks = taskRepository.findBySprintSprintId(sprintId);
        Map<Long, User> users = new LinkedHashMap<>();
        if (sprint.getProject() != null && sprint.getProject().getMembers() != null) {
            for (User member : sprint.getProject().getMembers()) {
                if (member.getRole() != User.Role.VIEWER) users.put(member.getId(), member);
            }
        }
        for (Task task : tasks) {
            if (task.getAssignee() != null && task.getAssignee().getRole() != User.Role.VIEWER) {
                users.put(task.getAssignee().getId(), task.getAssignee());
            }
        }

        int totalCapacity = users.values().stream()
                .map(User::getCapacityPoints)
                .mapToInt(val -> val == null ? 0 : val)
                .sum();
        int remaining = totalCapacity - planned;
        return new CapacityDTO(planned, totalCapacity, remaining, planned > totalCapacity);
    }

    @SuppressWarnings("unchecked")
    public VelocityDTO getVelocity(Long sprintId) {
        Sprint sprint = getSprint(sprintId);
        List<Task> tasks = taskRepository.findBySprintSprintId(sprintId);
        return (VelocityDTO) MetricCalculatorFactory.getCalculator("velocity").calculate(sprint, tasks, calculator);
    }

    @SuppressWarnings("unchecked")
    public List<WorkloadDTO> getWorkload(Long sprintId) {
        Sprint sprint = getSprint(sprintId);
        List<Task> tasks = taskRepository.findBySprintSprintId(sprintId);
        return (List<WorkloadDTO>) MetricCalculatorFactory.getCalculator("workload").calculate(sprint, tasks, calculator);
    }

    public List<BottleneckDTO> getBottlenecks(Long sprintId) {
        getSprint(sprintId);
        LocalDateTime now = LocalDateTime.now();
        return taskRepository.findBySprintSprintIdAndStatus(sprintId, Task.TaskStatus.IN_PROGRESS).stream()
                .map(task -> toBottleneck(task, now))
                .filter(bottleneck -> bottleneck.hoursInCurrentStatus() >= reviewThresholdHours)
                .toList();
    }

    public HealthDTO getHealth(Long sprintId) {
        Sprint sprint = getSprint(sprintId);
        List<Task> tasks = taskRepository.findBySprintSprintId(sprintId);
        return (HealthDTO) MetricCalculatorFactory.getCalculator("health").calculate(sprint, tasks, calculator);
    }

    public List<VelocityTrendDTO> getVelocityTrend(Long projectId, int limit) {
        getProject(projectId);
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 100");
        }
        List<Sprint> sprints = sprintRepository.findByProjectIdAndSprintStatusOrderByEndDateDesc(
                projectId, Sprint.SprintStatus.DONE, PageRequest.of(0, limit));
        Map<Long, List<Task>> tasksBySprint = sprints.isEmpty() ? Map.of() :
                taskRepository.findBySprintSprintIdIn(sprints.stream().map(Sprint::getSprintId).toList()).stream()
                        .collect(Collectors.groupingBy(task -> task.getSprint().getSprintId()));
        return sprints.stream()
                .map(sprint -> {
                    VelocityDTO velocity = (VelocityDTO) MetricCalculatorFactory.getCalculator("velocity")
                            .calculate(sprint, tasksBySprint.getOrDefault(sprint.getSprintId(), List.of()), calculator);
                    return new VelocityTrendDTO(
                            sprint.getSprintId(),
                            sprint.getObjective(),
                            sprint.getEndDate(),
                            velocity.plannedPoints(),
                            velocity.completedPoints(),
                            velocity.velocityRatio()
                    );
                })
                .sorted(Comparator.comparing(VelocityTrendDTO::endDate))
                .toList();
    }

    public Map<String, Object> getProjectMetrics(Long projectId) {
        getProject(projectId);

        List<Task> allTasks = taskRepository.findBySprintProjectId(projectId);
        long todoCount = allTasks.stream().filter(t -> t.getStatus() == Task.TaskStatus.TODO).count();
        long inProgressCount = allTasks.stream().filter(t -> t.getStatus() == Task.TaskStatus.IN_PROGRESS).count();
        long doneCount = allTasks.stream().filter(t -> t.getStatus() == Task.TaskStatus.DONE).count();

        Map<String, Long> completionRatios = new LinkedHashMap<>();
        completionRatios.put("TODO", todoCount);
        completionRatios.put("IN_PROGRESS", inProgressCount);
        completionRatios.put("DONE", doneCount);

        List<VelocityTrendDTO> velocityPatterns = getVelocityTrend(projectId, 5);

        Sprint activeSprint = sprintRepository.findByProjectIdAndIsActive(projectId, true).orElse(null);
        List<Map<String, Object>> burndown = new ArrayList<>();
        if (activeSprint != null) {
            List<Task> activeTasks = taskRepository.findBySprintSprintId(activeSprint.getSprintId());
            LocalDate start = activeSprint.getStartDate();
            LocalDate end = activeSprint.getEndDate();
            LocalDate lastActualDay = end.isBefore(LocalDate.now()) ? end : LocalDate.now();
            Map<LocalDate, Integer> changes = remainingPointChanges(activeTasks, start);
            int remaining = 0;
            // Bound legacy or corrupt sprint date ranges as well as the validated request path.
            for (LocalDate day = start; !day.isAfter(lastActualDay) && burndown.size() < 366; day = day.plusDays(1)) {
                remaining += changes.getOrDefault(day, 0);
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("day", day.toString());
                point.put("remainingPoints", Math.max(0, remaining));
                burndown.add(point);
            }
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("projectId", projectId);
        metrics.put("completionRatios", completionRatios);
        metrics.put("velocityPatterns", velocityPatterns);
        metrics.put("burndownPoints", burndown);
        return metrics;
    }

    private Sprint getSprint(Long sprintId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));
        authorizationService.requireProjectViewer(sprint.getProject().getId());
        return sprint;
    }

    private Projet getProject(Long projectId) {
        Projet project = projetRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        authorizationService.requireProjectViewer(projectId);
        return project;
    }

    private BottleneckDTO toBottleneck(Task task, LocalDateTime now) {
        LocalDateTime changedAt = task.getStatusChangedAt() == null ? task.getUpdatedAt() : task.getStatusChangedAt();
        double hours = changedAt == null ? 0.0 : Math.max(0.0, Duration.between(changedAt, now).toMinutes() / 60.0);
        return new BottleneckDTO(task.getId(), task.getTitle(), task.getStatus(), round(hours), reviewThresholdHours);
    }

    private Map<LocalDate, Integer> remainingPointChanges(List<Task> tasks, LocalDate start) {
        if (tasks.isEmpty()) return Map.of();
        Map<Long, List<TaskStatusHistory>> historyByTask = historyRepository
                .findByTaskIdInOrderByChangedAtAsc(tasks.stream().map(Task::getId).toList()).stream()
                .collect(Collectors.groupingBy(history -> history.getTask().getId()));
        Map<LocalDate, Integer> changes = new TreeMap<>();
        for (Task task : tasks) {
            int points = task.getEffortPoints() == null ? 0 : task.getEffortPoints();
            LocalDate created = task.getCreatedAt() == null ? start : task.getCreatedAt().toLocalDate();
            LocalDate added = created.isBefore(start) ? start : created;
            List<TaskStatusHistory> history = historyByTask.getOrDefault(task.getId(), List.of());
            if (history.isEmpty()) {
                changes.merge(added, points, Integer::sum);
                if (task.getStatus() == Task.TaskStatus.DONE) {
                    LocalDateTime completedAt = task.getStatusChangedAt() == null ? task.getUpdatedAt() : task.getStatusChangedAt();
                    if (completedAt != null) {
                        LocalDate completed = completedAt.toLocalDate().isBefore(added) ? added : completedAt.toLocalDate();
                        changes.merge(completed, -points, Integer::sum);
                    }
                }
                continue;
            }
            Task.TaskStatus initial = history.get(0).getFromStatus();
            if (initial != Task.TaskStatus.DONE) changes.merge(added, points, Integer::sum);
            for (TaskStatusHistory transition : history) {
                LocalDate day = transition.getChangedAt().toLocalDate().isBefore(added) ? added : transition.getChangedAt().toLocalDate();
                boolean wasDone = transition.getFromStatus() == Task.TaskStatus.DONE;
                boolean nowDone = transition.getToStatus() == Task.TaskStatus.DONE;
                if (wasDone != nowDone) changes.merge(day, nowDone ? -points : points, Integer::sum);
            }
        }
        return changes;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
