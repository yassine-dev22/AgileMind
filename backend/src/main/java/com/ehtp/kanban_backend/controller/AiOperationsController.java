package com.ehtp.kanban_backend.controller;

import com.ehtp.kanban_backend.dto.CopilotAnswerDTO;
import com.ehtp.kanban_backend.dto.CopilotAskRequestDTO;
import com.ehtp.kanban_backend.dto.SprintDiagnosticsRequestDTO;
import com.ehtp.kanban_backend.dto.SprintDiagnosticsResponseDTO;
import com.ehtp.kanban_backend.service.CopilotService;
import com.ehtp.kanban_backend.service.SprintDiagnosticsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/ai", "/api/ai"})
public class AiOperationsController {
    private final CopilotService copilotService;
    private final SprintDiagnosticsService diagnosticsService;

    public AiOperationsController(CopilotService copilotService, SprintDiagnosticsService diagnosticsService) {
        this.copilotService = copilotService;
        this.diagnosticsService = diagnosticsService;
    }

    @PostMapping("/copilot/ask")
    public CopilotAnswerDTO ask(@Valid @RequestBody CopilotAskRequestDTO request) {
        return copilotService.ask(request);
    }

    @PostMapping({"/sprintdiagnostics/trigger", "/sprint-diagnostics/trigger"})
    public ResponseEntity<SprintDiagnosticsResponseDTO> trigger(@Valid @RequestBody SprintDiagnosticsRequestDTO request) {
        SprintDiagnosticsResponseDTO result = diagnosticsService.trigger(request.sprintId());
        return ResponseEntity.status("SKIPPED".equals(result.status()) ? HttpStatus.OK : HttpStatus.ACCEPTED).body(result);
    }
}
