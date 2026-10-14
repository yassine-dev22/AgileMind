package com.ehtp.kanban_backend;

import com.ehtp.kanban_backend.dto.VelocityTrendDTO;
import com.ehtp.kanban_backend.dto.WorkloadDTO;
import com.ehtp.kanban_backend.model.Projet;
import com.ehtp.kanban_backend.model.Sprint;
import com.ehtp.kanban_backend.model.Task;
import com.ehtp.kanban_backend.model.TaskStatusHistory;
import com.ehtp.kanban_backend.model.User;
import com.ehtp.kanban_backend.repository.ProjetRepository;
import com.ehtp.kanban_backend.repository.SprintRepository;
import com.ehtp.kanban_backend.repository.TaskRepository;
import com.ehtp.kanban_backend.repository.TaskStatusHistoryRepository;
import com.ehtp.kanban_backend.service.AuthorizationService;
import com.ehtp.kanban_backend.service.MetricFormulaCalculator;
import com.ehtp.kanban_backend.service.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTests {
    @Mock SprintRepository sprints;
    @Mock ProjetRepository projects;
    @Mock TaskRepository tasks;
    @Mock TaskStatusHistoryRepository history;
    @Mock AuthorizationService authorization;
    MetricsService service;
    Projet project;
    Sprint sprint;

    @BeforeEach
    void setUp() {
        service = new MetricsService(sprints, projects, tasks, history, authorization, new MetricFormulaCalculator(), 48);
        project = new Projet();
        project.setId(1L);
        sprint = new Sprint();
        sprint.setSprintId(10L);
        sprint.setProject(project);
        sprint.setStartDate(LocalDate.now().minusDays(3));
        sprint.setEndDate(LocalDate.now().plusDays(5));
        sprint.setPointsPlanned(8);
        sprint.setObjective("Sprint objective");
    }

    @Test
    void everySprintMetricRejectsNonMembersBeforeReadingTaskData() {
        when(sprints.findById(10L)).thenReturn(Optional.of(sprint));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(authorization).requireProjectViewer(1L);
        List<Supplier<?>> endpoints = List.of(
                () -> service.getCapacity(10L), () -> service.getVelocity(10L),
                () -> service.getWorkload(10L), () -> service.getBottlenecks(10L),
                () -> service.getHealth(10L));
        for (Supplier<?> endpoint : endpoints) {
            assertThatThrownBy(endpoint::get).isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        verifyNoInteractions(tasks, history);
    }

    @Test
    void projectMetricsAndTrendsRejectNonMembers() {
        when(projects.findById(1L)).thenReturn(Optional.of(project));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(authorization).requireProjectViewer(1L);
        assertThatThrownBy(() -> service.getProjectMetrics(1L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.getVelocityTrend(1L, 5)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(sprints, tasks, history);
    }

    @Test
    void healthReadLoadsSprintAndTasksOnceWithoutWritingEntities() {
        when(sprints.findById(10L)).thenReturn(Optional.of(sprint));
        when(tasks.findBySprintSprintId(10L)).thenReturn(List.of(task(20L, sprint, Task.TaskStatus.DONE)));
        assertThat(service.getHealth(10L).score()).isEqualTo(100);
        verify(sprints).findById(10L);
        verify(tasks).findBySprintSprintId(10L);
        verifyNoMoreInteractions(sprints, tasks);
        verifyNoInteractions(history);
    }

    @Test
    void viewersDoNotInflateCapacityOrWorkloadBalance() {
        User member = new User("member@example.com", "Member", "unused", User.Role.MEMBER, 0);
        member.setId(30L);
        User viewer = new User("viewer@example.com", "Viewer", "unused", User.Role.VIEWER, 100);
        viewer.setId(31L);
        project.setMembers(List.of(member, viewer));
        Task assigned = task(20L, sprint, Task.TaskStatus.TODO);
        assigned.setAssignee(member);
        when(sprints.findById(10L)).thenReturn(Optional.of(sprint));
        when(tasks.findBySprintSprintId(10L)).thenReturn(List.of(assigned));
        assertThat(service.getCapacity(10L).totalCapacityPoints()).isZero();
        List<WorkloadDTO> workload = service.getWorkload(10L);
        assertThat(workload).extracting(WorkloadDTO::userId).containsExactly(30L);
        assertThat(workload.get(0).overCapacity()).isTrue();
    }

    @Test
    void trendsRejectInvalidLimits() {
        when(projects.findById(1L)).thenReturn(Optional.of(project));
        for (int limit : List.of(0, -1, 101, Integer.MAX_VALUE)) {
            assertThatThrownBy(() -> service.getVelocityTrend(1L, limit))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verifyNoInteractions(sprints, tasks);
    }

    @Test
    void trendsPageCompletedSprintsAndLoadTasksInOneQuery() {
        when(projects.findById(1L)).thenReturn(Optional.of(project));
        Sprint second = new Sprint();
        second.setSprintId(11L);
        second.setProject(project);
        second.setEndDate(sprint.getEndDate().plusDays(14));
        second.setPointsPlanned(8);
        when(sprints.findByProjectIdAndSprintStatusOrderByEndDateDesc(eq(1L), eq(Sprint.SprintStatus.DONE), any(Pageable.class)))
                .thenReturn(List.of(second, sprint));
        when(tasks.findBySprintSprintIdIn(List.of(11L, 10L)))
                .thenReturn(List.of(task(20L, sprint, Task.TaskStatus.DONE), task(21L, second, Task.TaskStatus.TODO)));
        List<VelocityTrendDTO> trend = service.getVelocityTrend(1L, 2);
        assertThat(trend).extracting(VelocityTrendDTO::sprintId).containsExactly(10L, 11L);
        assertThat(trend).extracting(VelocityTrendDTO::completedPoints).containsExactly(8, 0);
        verify(tasks).findBySprintSprintIdIn(List.of(11L, 10L));
        verifyNoMoreInteractions(tasks);
        verify(sprints, never()).findById(anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void burndownUsesStatusHistoryAndDoesNotInventFutureActuals() {
        when(projects.findById(1L)).thenReturn(Optional.of(project));
        when(sprints.findByProjectIdAndSprintStatusOrderByEndDateDesc(eq(1L), eq(Sprint.SprintStatus.DONE), any(Pageable.class)))
                .thenReturn(List.of());
        when(sprints.findByProjectIdAndIsActive(1L, true)).thenReturn(Optional.of(sprint));
        Task task = task(20L, sprint, Task.TaskStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(task, "createdAt", sprint.getStartDate().minusDays(1).atStartOfDay());
        when(tasks.findBySprintProjectId(1L)).thenReturn(List.of(task));
        when(tasks.findBySprintSprintId(10L)).thenReturn(List.of(task));
        when(history.findByTaskIdInOrderByChangedAtAsc(List.of(20L))).thenReturn(List.of(
                transition(task, Task.TaskStatus.TODO, Task.TaskStatus.DONE, sprint.getStartDate().plusDays(1)),
                transition(task, Task.TaskStatus.DONE, Task.TaskStatus.IN_PROGRESS, sprint.getStartDate().plusDays(2))));
        List<Map<String, Object>> points = (List<Map<String, Object>>) service.getProjectMetrics(1L).get("burndownPoints");
        assertThat(points).extracting(point -> point.get("remainingPoints")).containsExactly(8, 0, 8, 8);
        assertThat(points.get(points.size() - 1).get("day")).isEqualTo(LocalDate.now().toString());
    }

    private Task task(long id, Sprint taskSprint, Task.TaskStatus status) {
        Task task = new Task();
        task.setId(id);
        task.setSprint(taskSprint);
        task.setEffortPoints(8);
        task.setStatus(status);
        return task;
    }

    private TaskStatusHistory transition(Task task, Task.TaskStatus from, Task.TaskStatus to, LocalDate day) {
        TaskStatusHistory transition = new TaskStatusHistory();
        transition.setTask(task);
        transition.setFromStatus(from);
        transition.setToStatus(to);
        transition.setChangedAt(day.atTime(12, 0));
        return transition;
    }
}
