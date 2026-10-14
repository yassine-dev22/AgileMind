package com.ehtp.kanban_backend.controller;

import com.ehtp.kanban_backend.dto.DecisionNoteRequestDTO;
import com.ehtp.kanban_backend.model.DecisionNote;
import com.ehtp.kanban_backend.service.DecisionNoteService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/tasks/{taskId}/decision-notes")
public class DecisionNoteController {
    private final DecisionNoteService decisionNoteService;

    public DecisionNoteController(DecisionNoteService decisionNoteService) {
        this.decisionNoteService = decisionNoteService;
    }

    @PostMapping
    public DecisionNote createDecisionNote(
            @PathVariable Long taskId,
            @Valid @RequestBody DecisionNoteRequestDTO dto
    ) {
        return decisionNoteService.createDecisionNote(taskId, dto);
    }

    @GetMapping
    public List<DecisionNote> getDecisionNotes(@PathVariable Long taskId) {
        return decisionNoteService.getDecisionNotes(taskId);
    }
}
