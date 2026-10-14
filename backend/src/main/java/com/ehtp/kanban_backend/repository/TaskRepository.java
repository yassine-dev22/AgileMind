package com.ehtp.kanban_backend.repository;

import com.ehtp.kanban_backend.model.Sprint;
import com.ehtp.kanban_backend.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    @EntityGraph(attributePaths = {"assignee", "sprint", "requirement", "requirement.project"})
    Optional<Task> findById(Long id);

    @EntityGraph(attributePaths = {"assignee", "sprint", "requirement"})
    @Query("select distinct t from Task t left join t.requirement.project.members m where t.requirement.project.owner.id = :userId or m.id = :userId")
    List<Task> findVisibleToUser(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"assignee", "sprint", "requirement"})
    List<Task> findBySprintSprintId(Long sprintId);

    @EntityGraph(attributePaths = {"assignee", "sprint", "requirement"})
    List<Task> findBySprintProjectId(Long projectId);

    @EntityGraph(attributePaths = {"assignee", "sprint", "requirement"})
    List<Task> findBySprintProjectIdAndUpdatedAtAfter(Long projectId, LocalDateTime since);

    @EntityGraph(attributePaths = {"assignee", "sprint", "requirement"})
    List<Task> findByRequirementProjectIdAndUpdatedAtAfter(Long projectId, LocalDateTime since);

    @EntityGraph(attributePaths = {"assignee"})
    List<Task> findBySprintSprintIdIn(Collection<Long> sprintIds);

    boolean existsBySprintSprintIdAndStatusNot(Long sprintId, Task.TaskStatus status);

    @Query("select t.requirement.project.id from Task t where t.id = :id")
    Optional<Long> findProjectIdByTaskId(@Param("id") Long id);

    @Query("select coalesce(sum(t.effortPoints), 0) from Task t where t.sprint.sprintId = :id")
    Long sumEffortBySprintId(@Param("id") Long id);

    @Query("select coalesce(sum(t.effortPoints), 0) from Task t where t.requirement.id = :id")
    Long sumEffortByRequirementId(@Param("id") Long id);

    List<Task> findBySprintAndAssigneeIdAndStatusNot(Sprint sprint, Long assigneeId, Task.TaskStatus status);

    List<Task> findBySprintSprintIdAndAssigneeId(Long sprintId, Long assigneeId);

    List<Task> findBySprintSprintIdAndStatus(Long sprintId, Task.TaskStatus status);

    List<Task> findBySprintSprintIdAndStatusIn(Long sprintId, Collection<Task.TaskStatus> statuses);
}
