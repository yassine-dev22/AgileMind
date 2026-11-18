package com.ehtp.kanban_backend;

import com.ehtp.kanban_backend.model.ProjectMembership;
import com.ehtp.kanban_backend.model.Projet;
import com.ehtp.kanban_backend.model.Requirement;
import com.ehtp.kanban_backend.model.Sprint;
import com.ehtp.kanban_backend.model.Task;
import com.ehtp.kanban_backend.model.User;
import com.ehtp.kanban_backend.repository.*;
import com.ehtp.kanban_backend.service.AuthorizationService;
import com.ehtp.kanban_backend.service.MetricsService;
import com.ehtp.kanban_backend.service.ProjetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectMembershipTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProjetRepository projects;
    @Autowired ProjectMembershipRepository memberships;
    @Autowired SprintRepository sprints;
    @Autowired RequirementRepository requirements;
    @Autowired TaskRepository tasks;
    @Autowired AuthorizationService authorization;
    @Autowired ProjetService projectService;
    @Autowired MetricsService metrics;
    @Autowired EntityManager entityManager;
    User owner;
    User member;
    Projet project;
    Sprint sprint;
    Task task;

    @BeforeEach
    void seed() {
        owner = users.save(new User("membership-owner@example.com", "Owner", "unused", User.Role.MEMBER, 40));
        member = users.save(new User("membership-user@example.com", "Member", "unused", User.Role.MEMBER, 100));
        project = new Projet();
        project.setName("Membership project");
        project.setDescription("");
        project.setStartDate(LocalDate.now());
        project.setEndDate(LocalDate.now().plusDays(30));
        project.setStatus(Projet.ProjectStatus.PLANNED);
        project.setOwner(owner);
        project.getMembers().add(owner);
        project = projects.save(project);
        sprint = new Sprint();
        sprint.setProject(project);
        sprint.setObjective("Membership sprint");
        sprint.setStartDate(LocalDate.now());
        sprint.setEndDate(LocalDate.now().plusDays(14));
        sprint.setPointsPlanned(3);
        sprint = sprints.save(sprint);
        Requirement requirement = new Requirement();
        requirement.setProject(project);
        requirement.setSprint(sprint);
        requirement.setTitle("Membership requirement");
        requirement = requirements.save(requirement);
        task = new Task();
        task.setRequirement(requirement);
        task.setSprint(sprint);
        task.setTitle("Membership task");
        task.setStatus(Task.TaskStatus.TODO);
        task.setEffortPoints(3);
        task = tasks.save(task);
    }

    @Test
    void projectCreationPersistsOwnerMembershipAndLegacyRoster() throws Exception {
        mvc.perform(post("/api/projects").with(user(owner.getEmail())).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(projectPayload("Created project"))))
                .andExpect(status().isOk());
        Projet created = projects.findByOwnerId(owner.getId()).stream()
                .filter(candidate -> candidate.getName().equals("Created project")).findFirst().orElseThrow();
        assertThat(memberships.findByUserIdAndProjectId(owner.getId(), created.getId()).orElseThrow().getRole())
                .isEqualTo(ProjectMembership.Role.OWNER);
        assertThat(created.getMembers()).extracting(User::getId).contains(owner.getId());
    }

    @Test
    void invitationWritesMembershipOnceAndPreservesExistingViewerRole() {
        authenticate(owner);
        projectService.inviteMember(project.getId(), member.getEmail());
        projectService.inviteMember(project.getId(), member.getEmail());
        ProjectMembership saved = memberships.findByUserIdAndProjectId(member.getId(), project.getId()).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(ProjectMembership.Role.MEMBER);
        saved.setRole(ProjectMembership.Role.VIEWER);
        memberships.saveAndFlush(saved);
        projectService.inviteMember(project.getId(), member.getEmail());
        assertThat(memberships.findByProjectId(project.getId())).hasSize(1);
        assertThat(saved.getRole()).isEqualTo(ProjectMembership.Role.VIEWER);
        assertThat(project.getMembers()).extracting(User::getId).contains(member.getId());
    }

    @Test
    void membershipOnlyUsersCanReadProjectHomeTasksSprintsAndEffectiveRoster() throws Exception {
        persistRole(ProjectMembership.Role.MEMBER);
        mvc.perform(get("/api/projects").with(user(member.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(project.getId()));
        mvc.perform(get("/api/home").with(user(member.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.memberProjects[0].id").value(project.getId()));
        mvc.perform(get("/api/tasks").with(user(member.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(task.getId()));
        mvc.perform(get("/api/sprints").with(user(member.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].sprintId").value(sprint.getSprintId()));
        mvc.perform(get("/api/projects/" + project.getId() + "/detail").with(user(member.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.members[1].id").value(member.getId()))
                .andExpect(jsonPath("$.members[1].role").value("MEMBER"));
    }

    @Test
    void storedProjectViewerCannotWriteOrBeAssignedAndDoesNotInflateMetrics() throws Exception {
        persistRole(ProjectMembership.Role.VIEWER);
        mvc.perform(get("/api/projects/" + project.getId() + "/detail").with(user(member.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.members[1].role").value("VIEWER"));
        mvc.perform(patch("/api/tasks/" + task.getId() + "/status").with(user(member.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/tasks/" + task.getId() + "/assign").with(user(owner.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("assigneeId", member.getId()))))
                .andExpect(status().isBadRequest());
        authenticate(owner);
        assertThat(metrics.getCapacity(sprint.getSprintId()).totalCapacityPoints()).isEqualTo(40);
        assertThat(metrics.getWorkload(sprint.getSprintId())).extracting(workload -> workload.userId())
                .containsExactly(owner.getId());
    }

    @Test
    void controllerAndServiceBothRequireActualOwnerForProjectUpdates() throws Exception {
        // Even an inconsistent OWNER membership cannot replace projects.owner_id.
        persistRole(ProjectMembership.Role.OWNER);
        mvc.perform(put("/api/projects/" + project.getId()).with(user(member.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(projectPayload("Forbidden edit"))))
                .andExpect(status().isForbidden());
        authenticate(member);
        assertThatThrownBy(() -> authorization.requireProjectOwner(project.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        mvc.perform(put("/api/projects/" + project.getId()).with(user(owner.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(projectPayload("Owner edit"))))
                .andExpect(status().isOk());
    }

    @Test
    void globalViewerCapAlsoAppliesToPersistedOwnerRows() throws Exception {
        owner.setRole(User.Role.VIEWER);
        users.save(owner);
        memberships.save(new ProjectMembership(owner, project, ProjectMembership.Role.OWNER));
        entityManager.flush();
        entityManager.clear();
        mvc.perform(get("/api/projects/" + project.getId()).with(user(owner.getEmail())))
                .andExpect(status().isOk());
        mvc.perform(put("/api/projects/" + project.getId()).with(user(owner.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(projectPayload("Forbidden edit"))))
                .andExpect(status().isForbidden());
    }

    private void persistRole(ProjectMembership.Role role) {
        memberships.save(new ProjectMembership(member, project, role));
        entityManager.flush();
        entityManager.clear();
    }

    private Map<String, Object> projectPayload(String name) {
        return Map.of("name", name, "description", "", "startDate", LocalDate.now().toString(),
                "endDate", LocalDate.now().plusDays(30).toString(), "status", "PLANNED");
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), "unused", List.of()));
    }
}
