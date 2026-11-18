package com.ehtp.kanban_backend.service;

import com.ehtp.kanban_backend.dto.AiCallbackRequestDTO;
import com.ehtp.kanban_backend.dto.HealthDTO;
import com.ehtp.kanban_backend.dto.N8nSprintMetricsDTO;
import com.ehtp.kanban_backend.dto.SprintDiagnosticsResponseDTO;
import com.ehtp.kanban_backend.dto.VelocityDTO;
import com.ehtp.kanban_backend.model.Sprint;
import com.ehtp.kanban_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class WebhookDispatcherService {
    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcherService.class);

    private final MetricsService metricsService;
    private final AiInsightService aiInsightService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RestTemplate restTemplate;
    private final String webhookUrl;
    private final String webhookSecret;

    public WebhookDispatcherService(
            MetricsService metricsService,
            AiInsightService aiInsightService,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.n8n.webhook-url:}") String webhookUrl,
            @Value("${app.n8n.webhook-secret:}") String webhookSecret
    ) {
        this.metricsService = metricsService;
        this.aiInsightService = aiInsightService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.webhookUrl = webhookUrl;
        this.webhookSecret = webhookSecret;
        if (!webhookSecret.isBlank() && webhookSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("N8N_WEBHOOK_SECRET must contain at least 32 private random bytes");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public void dispatchSprintMetrics(Sprint sprint) {
        if (sprint == null) return;
        try {
            HealthDTO health = metricsService.getHealth(sprint.getSprintId());
            publishSnapshot(sprint, health);
        } catch (Exception exception) {
            // Failure of optional AI automation must not roll back the task or sprint change.
            log.error("Unable to build AI metric snapshot for sprint {}", sprint.getSprintId(), exception);
        }
    }

    public SprintDiagnosticsResponseDTO requestDiagnostics(Sprint sprint) {
        HealthDTO health = metricsService.getHealth(sprint.getSprintId());
        if (health.score() >= 60) {
            return new SprintDiagnosticsResponseDTO(sprint.getSprintId(), health.score(), sprint.getAnalysisRevision(),
                    "SKIPPED", "Diagnostics are triggered only when sprint health is below 60.");
        }
        publishSnapshot(sprint, health);
        boolean configured = !webhookUrl.isBlank() && !webhookSecret.isBlank();
        return new SprintDiagnosticsResponseDTO(sprint.getSprintId(), health.score(), sprint.getAnalysisRevision(),
                configured ? "QUEUED" : "LOCAL_FALLBACK", configured
                ? "Sprint metrics queued for delivery after commit; local insights are used if delivery fails."
                : "n8n is not configured; local insights will be saved after commit.");
    }

    private void publishSnapshot(Sprint sprint, HealthDTO health) {
        // Advance even after recovery so an older low-health callback cannot overwrite current insights.
        sprint.setAnalysisRevision(Math.incrementExact(sprint.getAnalysisRevision()));
        Long sprintId = sprint.getSprintId();
        VelocityDTO velocity = metricsService.getVelocity(sprintId);
        N8nSprintMetricsDTO payload = new N8nSprintMetricsDTO();
        payload.setProjectId(sprint.getProject().getId());
        payload.setSprintId(sprintId);
        payload.setAnalysisRevision(sprint.getAnalysisRevision());
        payload.setSprintName(sprint.getObjective());
        payload.setPlannedPoints(velocity.plannedPoints());
        payload.setCompletedPoints(velocity.completedPoints());
        payload.setVelocityRatio(velocity.velocityRatio());
        payload.setHealthScore(health.score());
        payload.setWorkloadBalance(health.components().workloadBalance());
        payload.setRemainingDays(Math.toIntExact(Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), sprint.getEndDate()))));
        payload.setBottlenecks(metricsService.getBottlenecks(sprintId));
        payload.setActiveSprintStatus(sprint.getSprintStatus().name());
        payload.setHistoricalVelocity(metricsService.getVelocityTrend(payload.getProjectId(), 5).stream()
                .map(trend -> trend.velocityRatio()).toList());
        payload.setResourceWorkloads(metricsService.getWorkload(sprintId).stream().map(workload -> {
            N8nSprintMetricsDTO.ResourceWorkload resource = new N8nSprintMetricsDTO.ResourceWorkload();
            resource.setUserId(workload.userId());
            resource.setFullName(workload.fullName());
            resource.setAssignedPoints(workload.assignedPoints());
            resource.setCapacityPoints(workload.capacityPoints());
            resource.setCompletedPoints(workload.completedPoints());
            resource.setRemainingPoints(workload.remainingPoints());
            resource.setLoadRatio(workload.loadRatio());
            resource.setOverCapacity(workload.overCapacity());
            resource.setOver120Percent(workload.over120Percent());
            return resource;
        }).toList());
        var emails = userRepository.findAllById(payload.getResourceWorkloads().stream()
                .map(N8nSprintMetricsDTO.ResourceWorkload::getUserId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(user -> user.getId(), user -> user.getEmail()));
        payload.getResourceWorkloads().forEach(resource -> resource.setEmail(emails.get(resource.getUserId())));
        eventPublisher.publishEvent(new SprintMetricsReady(payload, health));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void sendCommittedMetrics(SprintMetricsReady event) {
        if (event.health().score() >= 60 || webhookUrl.isBlank() || webhookSecret.isBlank()) {
            saveFallback(event);
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-LevelUP-Webhook-Token", webhookSecret);
            restTemplate.postForObject(webhookUrl, new HttpEntity<>(event.payload(), headers), String.class);
        } catch (Exception exception) {
            // Never log the endpoint: webhook paths may themselves be credentials.
            log.warn("AI webhook failed for sprint {}; using local insights", event.payload().getSprintId());
            saveFallback(event);
        }
    }

    private void saveFallback(SprintMetricsReady event) {
        try {
            AiCallbackRequestDTO dto = new AiCallbackRequestDTO();
            dto.setSprintId(event.payload().getSprintId());
            dto.setAnalysisRevision(event.payload().getAnalysisRevision());
            if (event.health().score() < 50) {
                dto.setPredictiveRisk("High risk. Sprint objective is at risk due to bottlenecks and workload imbalance.");
                dto.setReassignmentSuggestions("Review overloaded members and redistribute work where spare capacity exists.");
                dto.setHistoricalPatterns("Review cycle times and delivery trends before committing further scope.");
            } else if (event.health().score() < 60) {
                dto.setPredictiveRisk("Moderate risk. Review current velocity and workload balance.");
                dto.setReassignmentSuggestions("Review overloaded members before considering reassignment.");
                dto.setHistoricalPatterns("Monitor status transitions and bottlenecks.");
            } else {
                dto.setPredictiveRisk("Low risk from the current metrics; continue monitoring delivery.");
                dto.setReassignmentSuggestions("No reassignment is indicated by the aggregate health score.");
                dto.setHistoricalPatterns("Continue monitoring velocity and capacity matching.");
            }
            aiInsightService.processCallback(dto);
        } catch (Exception exception) {
            log.error("Unable to save fallback insights for sprint {}", event.payload().getSprintId(), exception);
        }
    }

    public record SprintMetricsReady(N8nSprintMetricsDTO payload, HealthDTO health) {}
}
