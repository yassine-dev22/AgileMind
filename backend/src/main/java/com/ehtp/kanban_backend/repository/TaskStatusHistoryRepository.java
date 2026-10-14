package com.ehtp.kanban_backend.repository;

import com.ehtp.kanban_backend.model.TaskStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Collection;

public interface TaskStatusHistoryRepository extends JpaRepository<TaskStatusHistory, Long> {
    List<TaskStatusHistory> findByTaskIdOrderByChangedAtDesc(Long taskId);
    @EntityGraph(attributePaths = {"task"})
    List<TaskStatusHistory> findByTaskIdInOrderByChangedAtAsc(Collection<Long> taskIds);
}
