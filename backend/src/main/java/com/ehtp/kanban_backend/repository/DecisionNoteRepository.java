package com.ehtp.kanban_backend.repository;

import com.ehtp.kanban_backend.model.DecisionNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface DecisionNoteRepository extends JpaRepository<DecisionNote, Long> {
    @EntityGraph(attributePaths = {"user", "task"})
    List<DecisionNote> findByTaskId(Long taskId);
}
