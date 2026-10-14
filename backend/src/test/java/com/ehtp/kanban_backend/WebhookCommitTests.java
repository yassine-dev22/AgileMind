package com.ehtp.kanban_backend;

import com.ehtp.kanban_backend.dto.AiCallbackRequestDTO;
import com.ehtp.kanban_backend.dto.HealthComponentDTO;
import com.ehtp.kanban_backend.dto.HealthDTO;
import com.ehtp.kanban_backend.dto.N8nSprintMetricsDTO;
import com.ehtp.kanban_backend.service.AiInsightService;
import com.ehtp.kanban_backend.service.WebhookDispatcherService.SprintMetricsReady;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class WebhookCommitTests {
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean AiInsightService insights;

    @Test
    void automationWaitsForCommitAndRetainsTheSnapshotRevision() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(snapshot());
            verifyNoInteractions(insights);
        });
        ArgumentCaptor<AiCallbackRequestDTO> callback = ArgumentCaptor.forClass(AiCallbackRequestDTO.class);
        verify(insights, timeout(2000)).processCallback(callback.capture());
        assertEquals(7L, callback.getValue().getAnalysisRevision());
        assertEquals(42L, callback.getValue().getSprintId());
    }

    @Test
    void rolledBackMutationsDoNotDispatchAutomation() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(snapshot());
            status.setRollbackOnly();
        });
        verify(insights, after(200).never()).processCallback(any());
    }

    private SprintMetricsReady snapshot() {
        N8nSprintMetricsDTO payload = new N8nSprintMetricsDTO();
        payload.setSprintId(42L);
        payload.setAnalysisRevision(7L);
        return new SprintMetricsReady(payload, new HealthDTO(90, "HEALTHY", new HealthComponentDTO(1.0, 1.0, 1.0), "Healthy"));
    }
}
