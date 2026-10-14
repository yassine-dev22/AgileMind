package com.ehtp.kanban_backend.controller;

import com.ehtp.kanban_backend.dto.AiCallbackRequestDTO;
import com.ehtp.kanban_backend.model.AiInsight;
import com.ehtp.kanban_backend.service.AiInsightService;
import com.ehtp.kanban_backend.security.AiCallbackVerifier;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiInsightService aiInsightService;
    private final AiCallbackVerifier callbackVerifier;

    public AiController(AiInsightService aiInsightService, AiCallbackVerifier callbackVerifier) {
        this.aiInsightService = aiInsightService;
        this.callbackVerifier = callbackVerifier;
    }

    @PostMapping("/callback")
    public void receiveCallback(
            @RequestHeader(value = "X-AI-Callback-Token", required = false) String callbackToken,
            @Valid @RequestBody AiCallbackRequestDTO dto
    ) {
        callbackVerifier.verify(callbackToken);
        aiInsightService.processCallback(dto);
    }

    @GetMapping("/sprint/{sprintId}")
    public List<AiInsight> getInsightsBySprint(@PathVariable Long sprintId) {
        return aiInsightService.getInsightsBySprint(sprintId);
    }
}
