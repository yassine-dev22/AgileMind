package com.ehtp.kanban_backend.service;

import com.ehtp.kanban_backend.dto.DigestResponseDTO;
import com.ehtp.kanban_backend.dto.ProjectDetailDTO;
import com.ehtp.kanban_backend.dto.ProjectRequestDTO;
import com.ehtp.kanban_backend.dto.UserDTO;
import com.ehtp.kanban_backend.model.Projet;
import com.ehtp.kanban_backend.model.Sprint;
import com.ehtp.kanban_backend.model.Task;
import com.ehtp.kanban_backend.model.User;
import com.ehtp.kanban_backend.repository.ProjetRepository;
import com.ehtp.kanban_backend.repository.SprintRepository;
import com.ehtp.kanban_backend.repository.TaskRepository;
import com.ehtp.kanban_backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ProjetService {

    private final ProjetRepository projetRepository;
    private final SprintRepository sprintRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final ProjectMutationGuard mutationGuard;

    public ProjetService(
            ProjetRepository projetRepository,
            SprintRepository sprintRepository,
            TaskRepository taskRepository,
            UserRepository userRepository,
            AuthorizationService authorizationService,
            ProjectMutationGuard mutationGuard
    ) {
        this.projetRepository = projetRepository;
        this.sprintRepository = sprintRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.mutationGuard = mutationGuard;
    }

    public Projet createProjet(ProjectRequestDTO dto) {
        User creator = authorizationService.currentUser();
        if (creator.getRole() == User.Role.VIEWER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A viewer cannot create projects");
        }
        Projet projet = new Projet();
        projet.setOwner(creator);
        if (projet.getMembers() == null) {
            projet.setMembers(new ArrayList<>());
        }
        projet.getMembers().add(creator);
        applyProjectFields(projet, dto);
        return projetRepository.save(projet);
    }

    @Transactional(readOnly = true)
    public List<Projet> getAllProjets() {
        User user = authorizationService.currentUser();
        return projetRepository.findDistinctByOwnerIdOrMembersId(user.getId(), user.getId());
    }

    @Transactional(readOnly = true)
    public Projet getProjetById(Long id) {
        Projet project = projetRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Projet not found"));
        authorizationService.requireProjectViewer(id);
        return project;
    }

    public Projet updateProjet(Long id, ProjectRequestDTO updatedProjet) {
        authorizationService.requireProjectOwner(id);
        Projet projet = mutationGuard.lock(id);

        applyProjectFields(projet, updatedProjet);

        return projetRepository.save(projet);
    }

    public void deleteProjet(Long id) {
        authorizationService.requireProjectOwner(id);
        Projet projet = mutationGuard.lock(id);
        projetRepository.delete(projet);
    }

    @Transactional(readOnly = true)
    public ProjectDetailDTO getProjectDetail(Long id) {
        Projet project = getProjetById(id);
        Sprint activeSprint = sprintRepository.findByProjectIdAndIsActive(id, true)
                .orElse(null);
        List<Task> tasks = activeSprint == null ? List.of() : taskRepository.findBySprintSprintId(activeSprint.getSprintId());
        List<UserDTO> memberDTOs = project.getMembers().stream().map(UserDTO::from).toList();
        return new ProjectDetailDTO(project, activeSprint, tasks, memberDTOs);
    }

    public Projet inviteMember(Long projectId, String email) {
        authorizationService.requireProjectOwner(projectId);
        Projet project = mutationGuard.lock(projectId);
        if (email == null || email.isBlank() || email.length() > 254) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email is required");
        }

        User invitee = userRepository.findByEmail(email.trim().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with email " + email + " not found"));

        if (project.getMembers().stream().noneMatch(member -> member.getId() == invitee.getId())) {
            project.getMembers().add(invitee);
        }

        return projetRepository.save(project);
    }

    @Transactional(readOnly = true)
    public DigestResponseDTO getDigest(Long projectId, int hours) {
        getProjetById(projectId);
        if (hours <= 0 || hours > 8760) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hours must be between 1 and 8760");
        }
        int safeHours = hours;
        LocalDateTime since = LocalDateTime.now().minusHours(safeHours);
        List<Task> changedTasks = taskRepository.findByRequirementProjectIdAndUpdatedAtAfter(projectId, since);
        List<String> highlights = new ArrayList<>();

        for (Task task : changedTasks) {
            String assignee = task.getAssignee() == null ? "unassigned" : task.getAssignee().getFullName();
            highlights.add("Task '" + task.getTitle() + "' is " + task.getStatus() + " and assigned to " + assignee + ".");
        }

        String summary = changedTasks.isEmpty()
                ? "No task changes were detected in the last " + safeHours + " hours."
                : changedTasks.size() + " task change(s) were detected in the last " + safeHours + " hours.";

        return new DigestResponseDTO(projectId, since, changedTasks.size(), summary, highlights);
    }

    private void applyProjectFields(Projet projet, ProjectRequestDTO dto) {
        validateProjectRequest(dto);
        projet.setName(dto.getName().trim());
        projet.setDescription(dto.getDescription() == null ? "" : dto.getDescription().trim());
        projet.setStartDate(dto.getStartDate());
        projet.setEndDate(dto.getEndDate());
        projet.setStatus(dto.getStatus() == null ? Projet.ProjectStatus.PLANNED : dto.getStatus());
    }

    private void validateProjectRequest(ProjectRequestDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank() || dto.getName().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (dto.getStartDate() == null || dto.getEndDate() == null || !dto.getStartDate().isBefore(dto.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before endDate");
        }
    }
}
