package com.ehtp.kanban_backend;

import com.ehtp.kanban_backend.dto.*;
import com.ehtp.kanban_backend.model.Projet;
import com.ehtp.kanban_backend.model.Sprint;
import com.ehtp.kanban_backend.model.Task;
import com.ehtp.kanban_backend.model.User;
import com.ehtp.kanban_backend.repository.UserRepository;
import com.ehtp.kanban_backend.service.AiInsightService;
import com.ehtp.kanban_backend.service.MetricsService;
import com.ehtp.kanban_backend.service.WebhookDispatcherService;
import com.ehtp.kanban_backend.service.WebhookDispatcherService.SprintMetricsReady;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SprintDiagnosticsTests {
    private final MetricsService metrics = mock(MetricsService.class);
    private final AiInsightService insights = mock(AiInsightService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private Sprint sprint;

    @BeforeEach
    void prepareMetrics() {
        Projet project = new Projet();
        project.setId(12L);
        sprint = new Sprint();
        sprint.setSprintId(42L);
        sprint.setProject(project);
        sprint.setObjective("Deliver login");
        sprint.setSprintStatus(Sprint.SprintStatus.ACTIVE);
        sprint.setEndDate(LocalDate.now().plusDays(4));
        sprint.setAnalysisRevision(6L);
        when(metrics.getVelocity(42L)).thenReturn(new VelocityDTO(20, 5, 0.25));
        when(metrics.getVelocityTrend(12L, 5)).thenReturn(List.of());
        when(metrics.getWorkload(42L)).thenReturn(List.of(
                new WorkloadDTO(7L, "Alice", 20, 5, 15, 10, 2.0, true, true)));
        when(metrics.getBottlenecks(42L)).thenReturn(List.of(
                new BottleneckDTO(9L, "Login review", Task.TaskStatus.IN_PROGRESS, 72.0, 48)));
        User user = new User("alice@example.com", "Alice", "unused", User.Role.MEMBER, 10);
        user.setId(7L);
        when(users.findAllById(any())).thenReturn(List.of(user));
    }

    @Test
    void lowHealthSnapshotIncludesRealMetricsAndAdvancesRevision() {
        when(metrics.getHealth(42L)).thenReturn(health(59));
        var dispatcher = dispatcher("http://n8n.internal/webhook", "test-webhook-token-at-least-32-bytes");

        var response = dispatcher.requestDiagnostics(sprint);

        ArgumentCaptor<SprintMetricsReady> event = ArgumentCaptor.forClass(SprintMetricsReady.class);
        verify(events).publishEvent(event.capture());
        var payload = event.getValue().payload();
        assertEquals("QUEUED", response.status());
        assertEquals(7L, response.analysisRevision());
        assertEquals(12L, payload.getProjectId());
        assertEquals(20, payload.getPlannedPoints());
        assertEquals(5, payload.getCompletedPoints());
        assertEquals(59, payload.getHealthScore());
        assertEquals(40.0, payload.getWorkloadBalance());
        assertEquals(4, payload.getRemainingDays());
        assertEquals(9L, payload.getBottlenecks().get(0).taskId());
        assertEquals("alice@example.com", payload.getResourceWorkloads().get(0).getEmail());
        assertTrue(payload.getResourceWorkloads().get(0).isOver120Percent());
    }

    @Test
    void exact60BoundarySkipsRequestedDiagnostics() {
        when(metrics.getHealth(42L)).thenReturn(health(60));
        var response = dispatcher("", "").requestDiagnostics(sprint);
        assertEquals("SKIPPED", response.status());
        assertEquals(6L, sprint.getAnalysisRevision());
        verifyNoInteractions(events, users);
        verify(metrics, never()).getVelocity(anyLong());
    }

    @Test
    void recoveredMutationInvalidatesOlderCallbacksAndUsesLocalInsights() {
        when(metrics.getHealth(42L)).thenReturn(health(60));
        var dispatcher = dispatcher("http://unused.invalid", "test-webhook-token-at-least-32-bytes");
        dispatcher.dispatchSprintMetrics(sprint);
        ArgumentCaptor<SprintMetricsReady> event = ArgumentCaptor.forClass(SprintMetricsReady.class);
        verify(events).publishEvent(event.capture());
        dispatcher.sendCommittedMetrics(event.getValue());
        ArgumentCaptor<AiCallbackRequestDTO> callback = ArgumentCaptor.forClass(AiCallbackRequestDTO.class);
        verify(insights).processCallback(callback.capture());
        assertEquals(7L, callback.getValue().getAnalysisRevision());
        assertTrue(callback.getValue().getPredictiveRisk().startsWith("Low risk"));
    }

    @Test
    void configuredLowHealthRequestPostsWebhookWithHeaderAuthentication() throws Exception {
        AtomicReference<String> receivedToken = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-LevelUP-Webhook-Token"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            when(metrics.getHealth(42L)).thenReturn(health(59));
            String token = "test-webhook-token-at-least-32-bytes";
            var dispatcher = dispatcher("http://127.0.0.1:" + server.getAddress().getPort() + "/webhook", token);
            dispatcher.requestDiagnostics(sprint);
            ArgumentCaptor<SprintMetricsReady> event = ArgumentCaptor.forClass(SprintMetricsReady.class);
            verify(events).publishEvent(event.capture());
            dispatcher.sendCommittedMetrics(event.getValue());
            assertEquals(token, receivedToken.get());
            assertTrue(receivedBody.get().contains("\"remainingDays\":4"));
            assertTrue(receivedBody.get().contains("\"analysisRevision\":7"));
            verifyNoInteractions(insights);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failingWebhookSavesRevisionMatchedLocalInsights() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            when(metrics.getHealth(42L)).thenReturn(health(59));
            var dispatcher = dispatcher("http://127.0.0.1:" + server.getAddress().getPort() + "/webhook",
                    "test-webhook-token-at-least-32-bytes");
            dispatcher.requestDiagnostics(sprint);
            ArgumentCaptor<SprintMetricsReady> event = ArgumentCaptor.forClass(SprintMetricsReady.class);
            verify(events).publishEvent(event.capture());
            dispatcher.sendCommittedMetrics(event.getValue());
            ArgumentCaptor<AiCallbackRequestDTO> callback = ArgumentCaptor.forClass(AiCallbackRequestDTO.class);
            verify(insights).processCallback(callback.capture());
            assertEquals(7L, callback.getValue().getAnalysisRevision());
            assertTrue(callback.getValue().getPredictiveRisk().startsWith("Moderate risk"));
        } finally {
            server.stop(0);
        }
    }

    private HealthDTO health(int score) {
        return new HealthDTO(score, "AT_RISK", new HealthComponentDTO(25.0, 40.0, 50.0), "Current metrics");
    }

    private WebhookDispatcherService dispatcher(String url, String token) {
        return new WebhookDispatcherService(metrics, insights, users, events, url, token);
    }
}
