package com.ehtp.kanban_backend.service;

import com.ehtp.kanban_backend.dto.AiCallbackRequestDTO;
import com.ehtp.kanban_backend.dto.HealthDTO;
import com.ehtp.kanban_backend.dto.N8nSprintMetricsDTO;
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
            // Build the snapshot while authentication and the persistence context are available.
            Long sprintId = sprint.getSprintId();
            sprint.setAnalysisRevision(Math.incrementExact(sprint.getAnalysisRevision()));
            HealthDTO health = metricsService.getHealth(sprintId);
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
            payload.setActiveSprintStatus(sprint.getSprintStatus().name());
            payload.setHistoricalVelocity(metricsService.getVelocityTrend(payload.getProjectId(), 5).stream()
                    .map(trend -> trend.velocityRatio()).toList());
            payload.setResourceWorkloads(metricsService.getWorkload(sprintId).stream().map(workload -> {
                N8nSprintMetricsDTO.ResourceWorkload resource = new N8nSprintMetricsDTO.ResourceWorkload();
                resource.setUserId(workload.userId());
                resource.setAssignedPoints(workload.assignedPoints());
                resource.setCapacityPoints(workload.capacityPoints());
                return resource;
            }).toList());
            var emails = userRepository.findAllById(payload.getResourceWorkloads().stream()
                    .map(N8nSprintMetricsDTO.ResourceWorkload::getUserId).toList()).stream()
                    .collect(java.util.stream.Collectors.toMap(user -> user.getId(), user -> user.getEmail()));
            payload.getResourceWorkloads().forEach(resource -> resource.setEmail(emails.get(resource.getUserId())));
            eventPublisher.publishEvent(new SprintMetricsReady(payload, health));
        } catch (Exception exception) {
            // Failure of optional AI automation must not roll back the task or sprint change.
            log.error("Unable to build AI metric snapshot for sprint {}", sprint.getSprintId(), exception);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void sendCommittedMetrics(SprintMetricsReady event) {
        if (webhookUrl.isBlank() || webhookSecret.isBlank()) {
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
            } else if (event.health().score() < 80) {
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
