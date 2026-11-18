package com.ehtp.kanban_backend.service;

import com.ehtp.kanban_backend.dto.SprintDiagnosticsResponseDTO;
import com.ehtp.kanban_backend.model.Sprint;
import com.ehtp.kanban_backend.repository.SprintRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SprintDiagnosticsService {
    private final SprintRepository sprintRepository;
    private final AuthorizationService authorizationService;
    private final ProjectMutationGuard mutationGuard;
    private final WebhookDispatcherService dispatcher;
    private final EntityManager entityManager;

    public SprintDiagnosticsService(SprintRepository sprintRepository, AuthorizationService authorizationService,
                                    ProjectMutationGuard mutationGuard, WebhookDispatcherService dispatcher,
                                    EntityManager entityManager) {
        this.sprintRepository = sprintRepository;
        this.authorizationService = authorizationService;
        this.mutationGuard = mutationGuard;
        this.dispatcher = dispatcher;
        this.entityManager = entityManager;
    }

    @Transactional
    public SprintDiagnosticsResponseDTO trigger(Long sprintId) {
        Long projectId = sprintRepository.findProjectIdBySprintId(sprintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));
        authorizationService.requireProjectEditor(projectId);
        mutationGuard.lock(projectId);
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));
        // A concurrent task update may have advanced the revision while this request waited for the lock.
        entityManager.refresh(sprint);
        return dispatcher.requestDiagnostics(sprint);
    }
}
