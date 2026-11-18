package com.ehtp.kanban_backend.service;

import com.ehtp.kanban_backend.dto.CopilotAnswerDTO;
import com.ehtp.kanban_backend.dto.CopilotAskRequestDTO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
@Validated
public class CopilotService {
    private final AuthorizationService authorizationService;
    private final RestClient restClient;
    private final String serviceToken;

    public CopilotService(
            AuthorizationService authorizationService,
            @Qualifier("copilotRestClient") RestClient restClient,
            @Value("${app.copilot.service-token:}") String serviceToken
    ) {
        this.authorizationService = authorizationService;
        this.restClient = restClient;
        this.serviceToken = serviceToken;
    }

    public CopilotAnswerDTO ask(@Valid CopilotAskRequestDTO request) {
        // Authorize before sending project data to the Python service. No user JWT is forwarded.
        authorizationService.requireProjectViewer(request.projectId());
        String question = request.question().replaceAll("\\p{Cc}", "").strip();
        if (question.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question must contain text");
        }
        if (serviceToken.isBlank()) throw unavailable();
        try {
            CopilotAnswerDTO answer = restClient.post().uri("/ask")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-RAG-Service-Token", serviceToken)
                    .body(new CopilotAskRequestDTO(question, request.projectId()))
                    .retrieve().body(CopilotAnswerDTO.class);
            if (answer == null || answer.answer() == null || answer.answer().isBlank() || answer.citations() == null) {
                throw unavailable();
            }
            return answer;
        } catch (RestClientException exception) {
            // Do not expose upstream bodies, URLs, credentials or provider errors to the browser.
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The project assistant is temporarily unavailable.");
    }
}
