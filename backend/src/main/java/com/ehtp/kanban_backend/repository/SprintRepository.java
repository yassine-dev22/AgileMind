package com.ehtp.kanban_backend.repository;

import com.ehtp.kanban_backend.model.Sprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, Long> {
    @Query("select s.project.id from Sprint s where s.sprintId = :id")
    Optional<Long> findProjectIdBySprintId(@Param("id") Long id);

    @EntityGraph(attributePaths = {"project"})
    @Query("select distinct s from Sprint s left join s.project.members m where s.project.owner.id = :userId or m.id = :userId")
    List<Sprint> findVisibleToUser(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"project", "tasks", "tasks.assignee"})
    Optional<Sprint> findById(Long id);

    @EntityGraph(attributePaths = {"project", "tasks", "tasks.assignee"})
    List<Sprint> findByProjectId(Long projectId);

    List<Sprint> findByProjectIdOrderByEndDateDesc(Long projectId);

    List<Sprint> findByProjectIdAndSprintStatusOrderByEndDateDesc(Long projectId, Sprint.SprintStatus status, Pageable pageable);

    Optional<Sprint> findByProjectIdAndSprintStatus(Long projectId, Sprint.SprintStatus sprintStatus);

    Optional<Sprint> findByProjectIdAndIsActive(Long projectId, Boolean isActive);
}
