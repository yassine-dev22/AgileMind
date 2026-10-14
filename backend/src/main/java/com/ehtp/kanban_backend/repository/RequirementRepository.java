package com.ehtp.kanban_backend.repository;

import com.ehtp.kanban_backend.model.Requirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

@Repository
public interface RequirementRepository extends JpaRepository<Requirement, Long> {
    @Query("select r.project.id from Requirement r where r.id = :id")
    Optional<Long> findProjectIdByRequirementId(@Param("id") Long id);
    @EntityGraph(attributePaths = {"project", "sprint", "subtasks", "subtasks.assignee"})
    Optional<Requirement> findById(Long id);

    @EntityGraph(attributePaths = {"project", "sprint", "subtasks", "subtasks.assignee"})
    List<Requirement> findAllByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = {"project", "sprint", "subtasks", "subtasks.assignee"})
    List<Requirement> findBySprintSprintId(Long sprintId);
    List<Requirement> findBySprintProjectId(Long projectId);
    @EntityGraph(attributePaths = {"project", "sprint", "subtasks", "subtasks.assignee"})
    List<Requirement> findByProjectId(Long projectId);
}
