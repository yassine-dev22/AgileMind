package com.ehtp.kanban_backend.service;

import com.ehtp.kanban_backend.dto.AiCallbackRequestDTO;
import com.ehtp.kanban_backend.dto.AiRiskIngestionDTO;
import com.ehtp.kanban_backend.model.AiInsight;
import com.ehtp.kanban_backend.model.Projet;
import com.ehtp.kanban_backend.model.Sprint;
import com.ehtp.kanban_backend.repository.AiInsightRepository;
import com.ehtp.kanban_backend.repository.ProjetRepository;
import com.ehtp.kanban_backend.repository.SprintRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.persistence.EntityManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Validated
public class AiInsightService {
    private final AiInsightRepository aiInsightRepository;
    private final SprintRepository sprintRepository;
    private final ProjetRepository projetRepository;
    private final ObjectMapper objectMapper;
    private final AuthorizationService authorizationService;
    private final EntityManager entityManager;

    public AiInsightService(
            AiInsightRepository aiInsightRepository, 
            SprintRepository sprintRepository,
            ProjetRepository projetRepository,
            ObjectMapper objectMapper,
            AuthorizationService authorizationService,
            EntityManager entityManager
    ) {
        this.aiInsightRepository = aiInsightRepository;
        this.sprintRepository = sprintRepository;
        this.projetRepository = projetRepository;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
        this.entityManager = entityManager;
    }

    @Transactional
    public void processCallback(@Valid AiCallbackRequestDTO dto) {
        if (dto.getSprintId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sprintId is required");
        }
        Sprint sprint = sprintRepository.findById(dto.getSprintId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));
        if (java.util.stream.Stream.of(dto.getPredictiveRisk(), dto.getReassignmentSuggestions(), dto.getHistoricalPatterns())
                .allMatch(value -> value == null || value.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one insight is required");
        }
        projetRepository.findByIdForUpdate(sprint.getProject().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        // The sprint was loaded before the project lock; refresh after waiting for concurrent changes.
        entityManager.refresh(sprint);
        if (dto.getAnalysisRevision() == null && sprint.getAnalysisRevision() != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "analysisRevision is required");
        }
        long callbackRevision = dto.getAnalysisRevision() == null ? 0 : dto.getAnalysisRevision();
        if (callbackRevision != sprint.getAnalysisRevision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "AI callback refers to an outdated or unknown analysis revision");
        }

        // Clear existing insights for this sprint to refresh
        List<AiInsight> existing = aiInsightRepository.findBySprintSprintId(sprint.getSprintId());
        aiInsightRepository.deleteAll(existing);

        // Save new insights
        if (dto.getPredictiveRisk() != null && !dto.getPredictiveRisk().isBlank()) {
            saveInsight(dto.getPredictiveRisk(), AiInsight.InsightType.RISK_ALERT, sprint);
        }
        if (dto.getReassignmentSuggestions() != null && !dto.getReassignmentSuggestions().isBlank()) {
            saveInsight(dto.getReassignmentSuggestions(), AiInsight.InsightType.REASSIGNMENT_SUGGESTION, sprint);
        }
        if (dto.getHistoricalPatterns() != null && !dto.getHistoricalPatterns().isBlank()) {
            saveInsight(dto.getHistoricalPatterns(), AiInsight.InsightType.PATTERN_ANALYSIS, sprint);
        }
    }

    @Transactional
    public AiInsight ingestProjectRiskInsight(Long projectId, @Valid AiRiskIngestionDTO dto) {
        authorizationService.requireProjectOwner(projectId);
        Projet project = projetRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        AiInsight insight = new AiInsight();
        insight.setProject(project);
        insight.setSprintHealthScore(dto.getSprintHealthScore());
        insight.setRiskLevel(dto.getRiskLevel());
        insight.setAutomatedStandupSummary(dto.getAutomatedStandupSummary());
        insight.setExplanation(dto.getAutomatedStandupSummary());
        insight.setConfidenceScore(100);
        insight.setAcknowledged(false);

        // Map alerts to string
        try {
            insight.setBottlenecksJson(objectMapper.writeValueAsString(dto.getBottleneckAlerts()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize AI bottleneck alerts", e);
        }

        return aiInsightRepository.save(insight);
    }

    @Transactional(readOnly = true)
    public List<AiInsight> getInsightsBySprint(Long sprintId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));
        authorizationService.requireProjectViewer(sprint.getProject().getId());
        return aiInsightRepository.findBySprintSprintId(sprintId);
    }

    @Transactional(readOnly = true)
    public List<AiInsight> getInsightsByProject(Long projectId) {
        authorizationService.requireProjectViewer(projectId);
        return aiInsightRepository.findByProjectId(projectId);
    }

    private void saveInsight(String text, AiInsight.InsightType type, Sprint sprint) {
        AiInsight insight = new AiInsight();
        insight.setExplanation(text);
        insight.setInsightType(type);
        insight.setConfidenceScore(85);
        insight.setAcknowledged(false);
        insight.setSprint(sprint);
        insight.setProject(sprint.getProject());
        aiInsightRepository.save(insight);
    }
}
