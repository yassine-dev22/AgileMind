package com.ehtp.kanban_backend.controller;

import com.ehtp.kanban_backend.dto.DigestResponseDTO;
import com.ehtp.kanban_backend.dto.ProjectDetailDTO;
import com.ehtp.kanban_backend.dto.ProjectRequestDTO;
import com.ehtp.kanban_backend.dto.RequirementRequestDTO;
import com.ehtp.kanban_backend.dto.SprintRequestDTO;
import com.ehtp.kanban_backend.dto.AiRiskIngestionDTO;
import com.ehtp.kanban_backend.model.Projet;
import com.ehtp.kanban_backend.model.Requirement;
import com.ehtp.kanban_backend.model.Sprint;
import com.ehtp.kanban_backend.model.AiInsight;
import com.ehtp.kanban_backend.service.ProjetService;
import com.ehtp.kanban_backend.service.RequirementService;
import com.ehtp.kanban_backend.service.SprintService;
import com.ehtp.kanban_backend.service.AiInsightService;
import com.ehtp.kanban_backend.service.MetricsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjetController {

    private final ProjetService projetService;
    private final RequirementService requirementService;
    private final SprintService sprintService;
    private final AiInsightService aiInsightService;
    private final MetricsService metricsService;

    public ProjetController(
            ProjetService projetService,
            RequirementService requirementService,
            SprintService sprintService,
            AiInsightService aiInsightService,
            MetricsService metricsService
    ) {
        this.projetService = projetService;
        this.requirementService = requirementService;
        this.sprintService = sprintService;
        this.aiInsightService = aiInsightService;
        this.metricsService = metricsService;
    }

    @PostMapping
    public Projet createProjet(@Valid @RequestBody ProjectRequestDTO projet) {
        return projetService.createProjet(projet);
    }

    @GetMapping
    public List<Projet> getAllProjets() {
        return projetService.getAllProjets();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@projectSecurity.hasRole(#id, 'VIEWER')")
    public Projet getProjetById(@PathVariable Long id) {
        return projetService.getProjetById(id);
    }

    @GetMapping("/{id}/detail")
    @PreAuthorize("@projectSecurity.hasRole(#id, 'VIEWER')")
    public ProjectDetailDTO getProjectDetail(@PathVariable Long id) {
        return projetService.getProjectDetail(id);
    }

    @GetMapping("/{id}/digest")
    @PreAuthorize("@projectSecurity.hasRole(#id, 'VIEWER')")
    public DigestResponseDTO getProjectDigest(
            @PathVariable Long id,
            @RequestParam(defaultValue = "24") int hours
    ) {
        return projetService.getDigest(id, hours);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@projectSecurity.hasRole(#id, 'MEMBER')")
    public Projet updateProjet(@PathVariable Long id, @Valid @RequestBody ProjectRequestDTO projet) {
        return projetService.updateProjet(id, projet);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@projectSecurity.hasRole(#id, 'OWNER')")
    public void deleteProjet(@PathVariable Long id) {
        projetService.deleteProjet(id);
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("@projectSecurity.hasRole(#id, 'OWNER')")
    public Projet inviteMember(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        return projetService.inviteMember(id, email);
    }

    @GetMapping("/{id}/requirements")
    @PreAuthorize("@projectSecurity.hasRole(#id, 'VIEWER')")
    public List<Requirement> getRequirementsByProject(@PathVariable Long id) {
        return requirementService.getRequirementsByProject(id);
    }

    @PostMapping("/{id}/requirements")
    @PreAuthorize("@projectSecurity.hasRole(#id, 'OWNER')")
    public Requirement addRequirement(@PathVariable Long id, @Valid @RequestBody RequirementRequestDTO reqDto) {
        return requirementService.createRequirementForProject(id, reqDto);
    }

    @PostMapping("/{id}/sprints")
    @PreAuthorize("@projectSecurity.hasRole(#id, 'OWNER')")
    public Sprint createSprint(@PathVariable Long id, @Valid @RequestBody SprintRequestDTO sprintDto) {
        return sprintService.createSprintForProject(id, sprintDto);
    }

    @GetMapping("/{id}/metrics")
    @PreAuthorize("@projectSecurity.hasRole(#id, 'VIEWER')")
    public Map<String, Object> getProjectMetrics(@PathVariable Long id) {
        return metricsService.getProjectMetrics(id);
    }

    @PostMapping("/{id}/ai-insights")
    @PreAuthorize("@projectSecurity.hasRole(#id, 'OWNER')")
    public AiInsight ingestProjectAiInsight(@PathVariable Long id, @Valid @RequestBody AiRiskIngestionDTO dto) {
        return aiInsightService.ingestProjectRiskInsight(id, dto);
    }
}
