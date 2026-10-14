package com.ehtp.kanban_backend.service;

import com.ehtp.kanban_backend.dto.RequirementRequestDTO;
import com.ehtp.kanban_backend.model.*;
import com.ehtp.kanban_backend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class RequirementService {
    private final RequirementRepository requirementRepository;
    private final SprintRepository sprintRepository;
    private final AuthorizationService authorizationService;
    private final ProjectMutationGuard mutationGuard;

    public RequirementService(RequirementRepository requirementRepository, SprintRepository sprintRepository,
                              AuthorizationService authorizationService, ProjectMutationGuard mutationGuard) {
        this.requirementRepository = requirementRepository;
        this.sprintRepository = sprintRepository;
        this.authorizationService = authorizationService;
        this.mutationGuard = mutationGuard;
    }

    public Requirement createRequirement(RequirementRequestDTO dto) {
        if (dto.getSprintId() == null) throw badRequest("sprintId is required for this creation endpoint");
        Long projectId = sprintRepository.findProjectIdBySprintId(dto.getSprintId()).orElseThrow(() -> notFound("Sprint"));
        authorizationService.requireProjectOwner(projectId);
        Projet project = mutationGuard.lock(projectId);
        Sprint sprint = sprintRepository.findById(dto.getSprintId()).orElseThrow(() -> notFound("Sprint"));
        requirePlanned(sprint);
        Requirement requirement = newRequirement(project, dto);
        requirement.setSprint(sprint);
        return requirementRepository.save(requirement);
    }

    public Requirement createRequirementForProject(Long projectId, RequirementRequestDTO dto) {
        authorizationService.requireProjectOwner(projectId);
        Projet project = mutationGuard.lock(projectId);
        if (dto.getSprintId() != null) throw badRequest("Use the sprint requirement endpoint to assign a sprint");
        return requirementRepository.save(newRequirement(project, dto));
    }

    private Requirement newRequirement(Projet project, RequirementRequestDTO dto) {
        validate(dto, true);
        Requirement requirement = new Requirement();
        requirement.setTitle(dto.getTitle().trim());
        requirement.setDescription(dto.getDescription() == null ? "" : dto.getDescription());
        requirement.setColor(dto.getColor() == null ? "#3b82f6" : dto.getColor());
        requirement.setProject(project);
        requirement.setStatus(Requirement.RequirementStatus.BACKLOG);
        return requirement;
    }

    @Transactional(readOnly = true)
    public List<Requirement> getRequirementsByProject(Long projectId) {
        authorizationService.requireProjectViewer(projectId);
        return requirementRepository.findByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public List<Requirement> getRequirementsBySprint(Long sprintId) {
        Long projectId = sprintRepository.findProjectIdBySprintId(sprintId).orElseThrow(() -> notFound("Sprint"));
        authorizationService.requireProjectViewer(projectId);
        return requirementRepository.findBySprintSprintId(sprintId);
    }

    @Transactional(readOnly = true)
    public Requirement getRequirementById(Long id) {
        Requirement requirement = requirement(id);
        authorizationService.requireProjectViewer(requirement.getProject().getId());
        return requirement;
    }

    public Requirement updateRequirement(Long id, RequirementRequestDTO dto) {
        Long projectId = lockForRequirement(id);
        Requirement requirement = requirement(id);
        mutationGuard.requireMutable(requirement.getSprint());
        validate(dto, false);
        if (dto.getTitle() != null) requirement.setTitle(dto.getTitle().trim());
        if (dto.getDescription() != null) requirement.setDescription(dto.getDescription());
        if (dto.getColor() != null) requirement.setColor(dto.getColor());
        if (dto.getSprintId() != null && (requirement.getSprint() == null || requirement.getSprint().getSprintId() != dto.getSprintId().longValue())) {
            Sprint previous = requirement.getSprint();
            if (previous != null) requirePlanned(previous);
            Sprint target = sprintRepository.findById(dto.getSprintId()).orElseThrow(() -> notFound("Sprint"));
            if (target.getProject().getId() != projectId.longValue()) throw badRequest("Sprint belongs to another project");
            requirePlanned(target);
            requirement.setSprint(target);
            requirement.getSubtasks().forEach(task -> task.setSprint(target));
            mutationGuard.updatePlanning(previous);
            mutationGuard.updatePlanning(target);
        }
        return requirementRepository.save(requirement);
    }

    public void deleteRequirement(Long id) {
        lockForRequirement(id);
        Requirement requirement = requirement(id);
        Sprint sprint = requirement.getSprint();
        if (sprint != null) requirePlanned(sprint);
        requirementRepository.delete(requirement);
        mutationGuard.updatePlanning(sprint);
    }

    private Long lockForRequirement(Long id) {
        Long projectId = requirementRepository.findProjectIdByRequirementId(id).orElseThrow(() -> notFound("Requirement"));
        authorizationService.requireProjectOwner(projectId);
        mutationGuard.lock(projectId);
        return projectId;
    }

    private void requirePlanned(Sprint sprint) {
        if (sprint.getSprintStatus() != Sprint.SprintStatus.PLANNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Requirement scope can only change in a planned sprint");
        }
    }

    private void validate(RequirementRequestDTO dto, boolean creating) {
        if ((creating || dto.getTitle() != null) && (dto.getTitle() == null || dto.getTitle().isBlank() || dto.getTitle().length() > 255)) throw badRequest("title must contain between 1 and 255 characters");
        if (dto.getDescription() != null && dto.getDescription().length() > 100000) throw badRequest("description exceeds 100000 characters");
        if (dto.getColor() != null && !dto.getColor().matches("#[0-9a-fA-F]{6}")) throw badRequest("color must be a six-digit hex color");
    }

    private Requirement requirement(Long id) { return requirementRepository.findById(id).orElseThrow(() -> notFound("Requirement")); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String resource) { return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " not found"); }
}
