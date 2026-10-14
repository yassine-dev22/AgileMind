package com.ehtp.kanban_backend;

import com.ehtp.kanban_backend.dto.*;
import com.ehtp.kanban_backend.exception.ActiveSprintExistsException;
import com.ehtp.kanban_backend.model.*;
import com.ehtp.kanban_backend.repository.*;
import com.ehtp.kanban_backend.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DomainAuditTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProjetRepository projects;
    @Autowired SprintRepository sprints;
    @Autowired RequirementRepository requirements;
    @SpyBean TaskRepository tasks;
    @Autowired UserRepository users;
    @Autowired DecisionNoteRepository notes;
    @Autowired TaskStatusHistoryRepository histories;
    @Autowired AiInsightRepository insights;
    @Autowired ProjectMembershipRepository memberships;
    @Autowired SprintService sprintService;
    @Autowired TaskService taskService;
    @Autowired ProjetService projectService;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean WebhookDispatcherService dispatcher;
    User owner;
    User stranger;
    Projet project;
    Projet foreignProject;

    @BeforeEach
    void setup() {
        reset(tasks);
        SecurityContextHolder.clearContext();
        projects.deleteAll();
        users.deleteAll();
        owner = users.save(new User("owner@domain.test", "Owner", "unused", User.Role.OWNER, 40));
        stranger = users.save(new User("stranger@domain.test", "Stranger", "unused", User.Role.OWNER, 40));
        project = project(owner, "Visible");
        foreignProject = project(stranger, "Foreign");
    }

    @Test
    void foreignObjectsCannotBeReadOrDeletedByAnotherGlobalOwner() throws Exception {
        Sprint visibleSprint = sprint(project, Sprint.SprintStatus.PLANNED);
        Sprint foreignSprint = sprint(foreignProject, Sprint.SprintStatus.PLANNED);
        Requirement visible = requirement(project, visibleSprint);
        Requirement foreign = requirement(foreignProject, foreignSprint);
        Task mine = task(visible, Task.TaskStatus.TODO, 3);
        Task theirs = task(foreign, Task.TaskStatus.TODO, 5);
        mvc.perform(get("/api/tasks").with(user(owner.getEmail()).roles("OWNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine.getId()));
        mvc.perform(get("/api/sprints").with(user(owner.getEmail()).roles("OWNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        for (String url : List.of("/api/tasks/" + theirs.getId(), "/api/sprints/" + foreignSprint.getSprintId(),
                "/api/requirements/" + foreign.getId(), "/api/requirements/sprint/" + foreignSprint.getSprintId(),
                "/api/tasks/" + theirs.getId() + "/decision-notes", "/api/tasks?sprintId=" + foreignSprint.getSprintId())) {
            mvc.perform(get(url).with(user(owner.getEmail()).roles("OWNER"))).andExpect(status().isForbidden());
        }
        mvc.perform(delete("/api/sprints/" + foreignSprint.getSprintId()).with(user(owner.getEmail()).roles("OWNER")))
                .andExpect(status().isForbidden());
        assertThat(sprints.existsById(foreignSprint.getSprintId())).isTrue();
    }

    @Test
    void foreignAssociationsAndAssigneesAreRejected() throws Exception {
        Sprint ownSprint = sprint(project, Sprint.SprintStatus.PLANNED);
        Sprint foreignSprint = sprint(foreignProject, Sprint.SprintStatus.PLANNED);
        Requirement ownRequirement = requirement(project, ownSprint);
        Requirement foreignRequirement = requirement(foreignProject, foreignSprint);
        mvc.perform(post("/api/tasks").with(user(owner.getEmail())).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", "Attack", "requirementId", foreignRequirement.getId(),
                        "sprintId", ownSprint.getSprintId())))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/requirements/" + ownRequirement.getId()).with(user(owner.getEmail()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("sprintId", foreignSprint.getSprintId()))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/requirements/" + ownRequirement.getId() + "/subtasks").with(user(owner.getEmail()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("title", "Task", "assigneeId", stranger.getId()))))
                .andExpect(status().isBadRequest());
        assertThat(tasks.count()).isZero();
    }

    @Test
    void backlogDecisionNoteUsesRequirementProjectAndAuthenticatedAuthor() throws Exception {
        Task backlogTask = task(requirement(project, null), Task.TaskStatus.TODO, 2);
        mvc.perform(post("/api/tasks/" + backlogTask.getId() + "/decision-notes").with(user(owner.getEmail()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("what", "Decision",
                        "why", "Reason", "decidedByUserId", stranger.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(owner.getId()));
        assertThat(notes.findByTaskId(backlogTask.getId())).hasSize(1);
    }

    @Test
    void invalidSubtaskAndOversizedNotePayloadsReturn400() throws Exception {
        Requirement backlog = requirement(project, null);
        for (Map<String, Object> payload : List.of(Map.<String, Object>of("title", " "),
                Map.<String, Object>of("title", "Task", "effortPoints", -1),
                Map.<String, Object>of("title", "Task", "estimatedHours", -1))) {
            mvc.perform(post("/api/requirements/" + backlog.getId() + "/subtasks").with(user(owner.getEmail()))
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }
        Task task = task(backlog, Task.TaskStatus.TODO, 1);
        mvc.perform(post("/api/tasks/" + task.getId() + "/decision-notes").with(user(owner.getEmail()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("what", "x".repeat(2001), "why", "Reason"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activeSprintEditPreservesDoneStatusTimestampAndBaseline() throws Exception {
        Sprint sprint = sprint(project, Sprint.SprintStatus.ACTIVE);
        Requirement requirement = requirement(project, sprint);
        Task done = task(requirement, Task.TaskStatus.DONE, 5);
        LocalDateTime timestamp = done.getStatusChangedAt();
        mvc.perform(put("/api/sprints/" + sprint.getSprintId()).with(user(owner.getEmail()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(sprintPayload(project,
                        Sprint.SprintStatus.ACTIVE, 5, List.of(requirement.getId()))))).andExpect(status().isOk());
        Task persisted = tasks.findById(done.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(Task.TaskStatus.DONE);
        assertThat(persisted.getStatusChangedAt()).isEqualTo(timestamp);
        mvc.perform(put("/api/sprints/" + sprint.getSprintId()).with(user(owner.getEmail()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(sprintPayload(project,
                        Sprint.SprintStatus.ACTIVE, 99, List.of(requirement.getId()))))).andExpect(status().isBadRequest());
    }

    @Test
    void putCannotBypassCompletionOrReopenCompletedSprint() throws Exception {
        Sprint sprint = sprint(project, Sprint.SprintStatus.ACTIVE);
        Requirement requirement = requirement(project, sprint);
        Task todo = task(requirement, Task.TaskStatus.TODO, 5);
        mvc.perform(put("/api/sprints/" + sprint.getSprintId()).with(user(owner.getEmail()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(sprintPayload(project,
                        Sprint.SprintStatus.DONE, 5, List.of(requirement.getId()))))).andExpect(status().isBadRequest());
        todo.setStatus(Task.TaskStatus.DONE);
        tasks.save(todo);
        mvc.perform(patch("/api/sprints/" + sprint.getSprintId() + "/complete").with(user(owner.getEmail())))
                .andExpect(status().isOk());
        mvc.perform(put("/api/sprints/" + sprint.getSprintId()).with(user(owner.getEmail()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(sprintPayload(project,
                        Sprint.SprintStatus.ACTIVE, 5, List.of(requirement.getId()))))).andExpect(status().isConflict());
    }

    @Test
    void activationFreezesDerivedEffortAndPlanningCanCoexistWithActiveSprint() {
        Requirement backlog = requirement(project, null);
        task(backlog, Task.TaskStatus.TODO, 8);
        authenticate(owner);
        SprintRequestDTO activeRequest = sprintRequest(project, Sprint.SprintStatus.ACTIVE, 999, List.of(backlog.getId()));
        Sprint active = sprintService.createSprint(activeRequest);
        assertThat(active.getPointsPlanned()).isEqualTo(8);
        assertThat(active.getRequirements()).hasSize(1);
        assertThat(active.getTasks()).hasSize(1);
        assertThat(tasks.findBySprintSprintId(active.getSprintId())).hasSize(1);
        Sprint planned = sprintService.createSprint(sprintRequest(project, Sprint.SprintStatus.PLANNED, 0, List.of()));
        assertThat(planned.getSprintStatus()).isEqualTo(Sprint.SprintStatus.PLANNED);
    }

    @Test
    void plannedSprintActivationFreezesActualScopeRatherThanClientTotal() {
        Sprint planned = sprint(project, Sprint.SprintStatus.PLANNED);
        Requirement requirement = requirement(project, planned);
        task(requirement, Task.TaskStatus.TODO, 13);
        authenticate(owner);
        Sprint activated = sprintService.updateSprint(planned.getSprintId(),
                sprintRequest(project, Sprint.SprintStatus.ACTIVE, 999, List.of(requirement.getId())));
        assertThat(activated.getPointsPlanned()).isEqualTo(13);
        assertThat(activated.getSprintStatus()).isEqualTo(Sprint.SprintStatus.ACTIVE);
    }

    @Test
    void zeroCapacityMemberTriggersAssignmentWarningAndViewersCannotEdit() throws Exception {
        User member = users.save(new User("zero@domain.test", "Zero", "unused", User.Role.MEMBER, 0));
        User viewer = users.save(new User("viewer@domain.test", "Viewer", "unused", User.Role.VIEWER, 40));
        project.getMembers().add(member);
        project.getMembers().add(viewer);
        project = projects.save(project);
        Sprint active = sprint(project, Sprint.SprintStatus.ACTIVE);
        Task task = task(requirement(project, active), Task.TaskStatus.TODO, 5);
        mvc.perform(post("/api/tasks/" + task.getId() + "/assign").with(user(owner.getEmail()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("assigneeId", member.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.overAssigned").value(true));
        mvc.perform(patch("/api/tasks/" + task.getId() + "/status").with(user(viewer.getEmail()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingPlannedSprintRetainsBacklogAndDeletingProjectCleansReferences() {
        Sprint sprint = sprint(project, Sprint.SprintStatus.PLANNED);
        Requirement requirement = requirement(project, sprint);
        Task task = task(requirement, Task.TaskStatus.TODO, 5);
        authenticate(owner);
        sprintService.deleteSprint(sprint.getSprintId());
        assertThat(requirements.findById(requirement.getId()).orElseThrow().getSprint()).isNull();
        assertThat(tasks.findById(task.getId()).orElseThrow().getSprint()).isNull();
        AiInsight insight = new AiInsight();
        insight.setProject(project);
        insight.setInsightType(AiInsight.InsightType.RISK_ALERT);
        insights.save(insight);
        memberships.save(new ProjectMembership(owner, project, ProjectMembership.Role.OWNER));
        Sprint nestedSprint = sprint(project, Sprint.SprintStatus.ACTIVE);
        Task nestedTask = task(requirement(project, nestedSprint), Task.TaskStatus.DONE, 3);
        TaskStatusHistory history = new TaskStatusHistory();
        history.setTask(nestedTask);
        history.setToStatus(Task.TaskStatus.DONE);
        history.setChangedAt(LocalDateTime.now());
        history.setChangedBy(owner);
        histories.save(history);
        DecisionNote note = new DecisionNote();
        note.setTask(nestedTask);
        note.setUser(owner);
        note.setWhat("Nested");
        note.setWhy("Cascade regression");
        notes.save(note);
        AiInsight sprintInsight = new AiInsight();
        sprintInsight.setProject(project);
        sprintInsight.setSprint(nestedSprint);
        sprintInsight.setInsightType(AiInsight.InsightType.RISK_ALERT);
        insights.save(sprintInsight);
        projectService.deleteProjet(project.getId());
        assertThat(requirements.existsById(requirement.getId())).isFalse();
        assertThat(tasks.existsById(task.getId())).isFalse();
        assertThat(insights.findByProjectId(project.getId())).isEmpty();
        assertThat(memberships.findByProjectId(project.getId())).isEmpty();
        assertThat(sprints.existsById(nestedSprint.getSprintId())).isFalse();
        assertThat(tasks.existsById(nestedTask.getId())).isFalse();
        assertThat(histories.findByTaskIdOrderByChangedAtDesc(nestedTask.getId())).isEmpty();
        assertThat(notes.findByTaskId(nestedTask.getId())).isEmpty();
    }

    @Test
    void statusAndHistoryRollbackTogetherWhenTaskWriteFails() {
        Task task = task(requirement(project, null), Task.TaskStatus.TODO, 3);
        authenticate(owner);
        TaskStatusUpdateDTO change = new TaskStatusUpdateDTO();
        change.setStatus(Task.TaskStatus.IN_PROGRESS);
        doThrow(new IllegalStateException("simulated storage failure")).when(tasks).save(any(Task.class));
        assertThatThrownBy(() -> taskService.updateStatus(task.getId(), change)).isInstanceOf(IllegalStateException.class);
        reset(tasks);
        assertThat(tasks.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(Task.TaskStatus.TODO);
        assertThat(histories.findByTaskIdOrderByChangedAtDesc(task.getId())).isEmpty();
    }

    @Test
    void concurrentActivationsCreateExactlyOneActiveSprint() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> activate = () -> {
            authenticate(owner);
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                sprintService.createSprint(sprintRequest(project, Sprint.SprintStatus.ACTIVE, 0, List.of()));
                return true;
            } catch (ActiveSprintExistsException expected) {
                return false;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
        try {
            Future<Boolean> first = workers.submit(activate);
            Future<Boolean> second = workers.submit(activate);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int successes = (first.get(10, TimeUnit.SECONDS) ? 1 : 0) + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successes).isEqualTo(1);
            assertThat(sprints.findByProjectId(project.getId()).stream().filter(Sprint::getIsActive)).hasSize(1);
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void taskReadFetchesToOneRelationsWithoutPerTaskQueries() {
        Sprint sprint = sprint(project, Sprint.SprintStatus.PLANNED);
        Requirement requirement = requirement(project, sprint);
        for (int index = 0; index < 15; index++) {
            User assignee = users.save(new User("member" + index + "@domain.test", "Member", "unused", User.Role.MEMBER, 40));
            Task task = task(requirement, Task.TaskStatus.TODO, 1);
            task.setAssignee(assignee);
            tasks.save(task);
        }
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(ignored -> {
            entityManager.clear();
            var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
            statistics.setStatisticsEnabled(true);
            statistics.clear();
            List<Task> loaded = tasks.findBySprintSprintId(sprint.getSprintId());
            loaded.forEach(task -> { task.getAssignee().getFullName(); task.getRequirement().getTitle(); task.getSprint().getObjective(); });
            assertThat(loaded).hasSize(15);
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
            statistics.setStatisticsEnabled(false);
        });
    }

    private Projet project(User owner, String name) {
        Projet project = new Projet();
        project.setOwner(owner);
        project.getMembers().add(owner);
        project.setName(name);
        project.setDescription("");
        project.setStartDate(LocalDate.now());
        project.setEndDate(LocalDate.now().plusDays(30));
        project.setStatus(Projet.ProjectStatus.PLANNED);
        return projects.save(project);
    }

    private Sprint sprint(Projet project, Sprint.SprintStatus status) {
        Sprint sprint = new Sprint();
        sprint.setProject(project);
        sprint.setObjective("Objective");
        sprint.setStartDate(LocalDate.now());
        sprint.setEndDate(LocalDate.now().plusDays(14));
        sprint.setPointsPlanned(5);
        sprint.setSprintStatus(status);
        return sprints.save(sprint);
    }

    private Requirement requirement(Projet project, Sprint sprint) {
        Requirement requirement = new Requirement();
        requirement.setProject(project);
        requirement.setSprint(sprint);
        requirement.setTitle("Requirement");
        return requirements.save(requirement);
    }

    private Task task(Requirement requirement, Task.TaskStatus status, int effort) {
        Task task = new Task();
        task.setRequirement(requirement);
        task.setSprint(requirement.getSprint());
        task.setTitle("Task");
        task.setStatus(status);
        task.setEffortPoints(effort);
        task.setStatusChangedAt(LocalDateTime.of(2026, 10, 7, 12, 0));
        return tasks.save(task);
    }

    private SprintRequestDTO sprintRequest(Projet project, Sprint.SprintStatus status, int points, List<Long> ids) {
        SprintRequestDTO request = new SprintRequestDTO();
        request.setProjectId(project.getId());
        request.setObjective("Objective");
        request.setStartDate(LocalDate.now());
        request.setEndDate(LocalDate.now().plusDays(14));
        request.setPointsPlanned(points);
        request.setSprintStatus(status);
        request.setRequirementIds(ids);
        return request;
    }

    private Map<String, Object> sprintPayload(Projet project, Sprint.SprintStatus status, int points, List<Long> ids) {
        return Map.of("projectId", project.getId(), "objective", "Objective", "startDate", LocalDate.now().toString(),
                "endDate", LocalDate.now().plusDays(14).toString(), "pointsPlanned", points, "sprintStatus", status.name(), "requirementIds", ids);
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getEmail(), "unused", List.of()));
    }
}
