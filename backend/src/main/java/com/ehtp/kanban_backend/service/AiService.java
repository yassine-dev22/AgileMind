package com.ehtp.kanban_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ehtp.kanban_backend.dto.BottleneckDTO;
import com.ehtp.kanban_backend.dto.CapacityDTO;
import com.ehtp.kanban_backend.dto.HealthDTO;
import com.ehtp.kanban_backend.dto.ReassignmentSuggestionsDTO;
import com.ehtp.kanban_backend.dto.SprintRiskDTO;
import com.ehtp.kanban_backend.dto.SprintRetrospectiveDTO;
import com.ehtp.kanban_backend.dto.SuggestedReassignmentDTO;
import com.ehtp.kanban_backend.dto.VelocityTrendDTO;
import com.ehtp.kanban_backend.dto.WorkloadDTO;
import com.ehtp.kanban_backend.model.Task;
import com.ehtp.kanban_backend.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class AiService {
    private final MetricsService metricsService;
    private final TaskRepository taskRepository;
    private final GeminiClient geminiClient;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public AiService(
            MetricsService metricsService,
            TaskRepository taskRepository,
            GeminiClient geminiClient,
            PromptBuilder promptBuilder,
            ObjectMapper objectMapper
    ) {
        this.metricsService = metricsService;
        this.taskRepository = taskRepository;
        this.geminiClient = geminiClient;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    public SprintRiskDTO sprintRisk(Long sprintId) {
        HealthDTO health = metricsService.getHealth(sprintId);
        CapacityDTO capacity = metricsService.getCapacity(sprintId);
        int remainingWork = metricsService.getWorkload(sprintId).stream()
                .map(WorkloadDTO::remainingPoints)
                .mapToInt(value -> value == null ? 0 : value)
                .sum();

        Optional<SprintRiskDTO> gemini = geminiClient
                .requestJson(promptBuilder.sprintRiskPrompt(health, remainingWork, capacity.remainingCapacity()))
                .flatMap(node -> convert(node, SprintRiskDTO.class));
        return gemini.orElseGet(() -> localRisk(health, remainingWork, capacity.remainingCapacity()));
    }

    public ReassignmentSuggestionsDTO reassignmentSuggestions(Long sprintId) {
        List<WorkloadDTO> workload = metricsService.getWorkload(sprintId);
        List<Task> tasks = taskRepository.findBySprintSprintId(sprintId);
        List<BottleneckDTO> bottlenecks = metricsService.getBottlenecks(sprintId);

        Optional<ReassignmentSuggestionsDTO> gemini = geminiClient
                .requestJson(promptBuilder.reassignmentPrompt(workload, tasks, bottlenecks))
                .flatMap(node -> convert(node, ReassignmentSuggestionsDTO.class));
        return gemini.orElseGet(() -> localReassignment(workload, tasks));
    }

    public SprintRetrospectiveDTO sprintRetrospective(Long projectId) {
        List<VelocityTrendDTO> trend = metricsService.getVelocityTrend(projectId, 5);
        Optional<SprintRetrospectiveDTO> gemini = geminiClient
                .requestJson(promptBuilder.retrospectivePrompt(trend))
                .flatMap(node -> convert(node, SprintRetrospectiveDTO.class));
        return gemini.orElseGet(() -> localRetrospective(trend));
    }

    private SprintRiskDTO localRisk(HealthDTO health, int remainingWork, int remainingCapacity) {
        if (health.score() < 50) {
            return new SprintRiskDTO(
                    "HIGH",
                    "Sprint health is critical at " + health.score() + ".",
                    "Velocity, workload balance, or timing is below the expected threshold.",
                    "Reduce scope, clear review bottlenecks, and rebalance overloaded members."
            );
        }
        if (health.score() < 60 || remainingWork > remainingCapacity) {
            return new SprintRiskDTO(
                    "MEDIUM",
                    "The sprint is at risk because remaining work is close to or above available capacity.",
                    remainingWork > remainingCapacity ? "Remaining work exceeds remaining capacity." : "Health score is below the safe threshold.",
                    "Prioritize the highest-value tasks and move excess work back to backlog."
            );
        }
        return new SprintRiskDTO(
                "LOW",
                "No major sprint risk detected from current metrics.",
                "Velocity, timing, and workload are currently acceptable.",
                "Keep monitoring review time and workload distribution."
        );
    }

    private ReassignmentSuggestionsDTO localReassignment(List<WorkloadDTO> workload, List<Task> tasks) {
        Optional<WorkloadDTO> overloaded = workload.stream()
                .filter(WorkloadDTO::over120Percent)
                .max(Comparator.comparing(WorkloadDTO::loadRatio));
        if (overloaded.isEmpty()) {
            return new ReassignmentSuggestionsDTO(
                    List.of(),
                    "No clear reassignment is recommended from the current workload distribution.",
                    0
            );
        }

        WorkloadDTO source = overloaded.get();
        Optional<MoveCandidate> candidate = tasks.stream()
                .filter(task -> task.getAssignee() != null && task.getAssignee().getId() == source.userId())
                .filter(task -> task.getStatus() != Task.TaskStatus.DONE)
                .filter(task -> task.getEffortPoints() != null && task.getEffortPoints() > 0)
                .sorted(Comparator.comparing(Task::getEffortPoints).reversed())
                .flatMap(task -> workload.stream()
                        .filter(user -> !java.util.Objects.equals(user.userId(), source.userId()))
                        .filter(user -> !user.overCapacity() && user.capacityPoints() != null && user.assignedPoints() != null)
                        .filter(user -> (long) user.capacityPoints() - user.assignedPoints() >= task.getEffortPoints())
                        .min(Comparator.comparing(WorkloadDTO::loadRatio))
                        .map(user -> new MoveCandidate(task, user)).stream())
                .findFirst();

        if (candidate.isEmpty()) {
            return new ReassignmentSuggestionsDTO(List.of(), "No active task fits within another member's spare capacity.", 0);
        }

        Task task = candidate.get().task();
        WorkloadDTO target = candidate.get().target();
        SuggestedReassignmentDTO suggestion = new SuggestedReassignmentDTO(
                task.getId(),
                task.getTitle(),
                source.userId(),
                source.fullName(),
                target.userId(),
                target.fullName(),
                "Move a high-effort active task from the most overloaded member to the lowest-load available member."
        );
        return new ReassignmentSuggestionsDTO(
                List.of(suggestion),
                "This redistributes work without exceeding the receiving member's capacity.",
                0
        );
    }

    private record MoveCandidate(Task task, WorkloadDTO target) {}

    private SprintRetrospectiveDTO localRetrospective(List<VelocityTrendDTO> trend) {
        if (trend.isEmpty()) {
            return new SprintRetrospectiveDTO(
                    List.of("No completed sprint velocity data is available yet."),
                    "The team needs more sprint history before trend analysis is meaningful.",
                    "Capture velocity snapshots at each sprint close."
            );
        }

        double average = trend.stream().mapToDouble(VelocityTrendDTO::velocityRatio).average().orElse(0.0);
        String pattern = average >= 0.8
                ? "Velocity is generally strong across recent sprints."
                : "Velocity is below target across recent sprints.";
        return new SprintRetrospectiveDTO(
                List.of(pattern, "Trend is based on the latest " + trend.size() + " sprint(s)."),
                average >= 0.8 ? "Delivery is broadly predictable." : "Delivery predictability needs attention.",
                average >= 0.8 ? "Keep current planning discipline." : "Reduce planned scope or address recurring blockers before sprint start."
        );
    }

    private <T> Optional<T> convert(JsonNode node, Class<T> type) {
        try {
            return Optional.of(objectMapper.convertValue(node, type));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
