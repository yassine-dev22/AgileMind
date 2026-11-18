package com.ehtp.kanban_backend;

import com.ehtp.kanban_backend.dto.CopilotAskRequestDTO;
import com.ehtp.kanban_backend.service.AuthorizationService;
import com.ehtp.kanban_backend.service.CopilotService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CopilotServiceTests {
    private static final String TOKEN = "test-rag-service-token-at-least-32-bytes";

    @Test
    void authorizedQuestionDelegatesProjectAndPreservesCitations() {
        AuthorizationService authorization = mock(AuthorizationService.class);
        RestClient.Builder builder = RestClient.builder().baseUrl("http://rag.internal:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://rag.internal:8000/ask"))
                .andExpect(header("X-RAG-Service-Token", TOKEN))
                .andExpect(content().json("{\"question\":\"Why did task 7 stop?\",\"project_id\":12}"))
                .andRespond(withSuccess("""
                        {"answer":"Task 7 needed a review.","citations":[
                          {"source_type":"DecisionNote","source_id":7,"author":"Alice",
                           "date":"2026-10-14T10:00:00","chunk_index":0}]}
                        """, MediaType.APPLICATION_JSON));
        var service = new CopilotService(authorization, builder.build(), TOKEN);

        var response = service.ask(new CopilotAskRequestDTO("  Why did task 7 stop?\n", 12L));

        verify(authorization).requireProjectViewer(12L);
        assertEquals("Task 7 needed a review.", response.answer());
        assertEquals("DecisionNote", response.citations().get(0).sourceType());
        assertEquals("Alice", response.citations().get(0).author());
        assertEquals(7L, response.citations().get(0).sourceId());
        assertEquals("2026-10-14T10:00:00", response.citations().get(0).date());
        server.verify();
    }

    @Test
    void outsiderIsRejectedBeforeAnyOutboundRequest() {
        AuthorizationService authorization = mock(AuthorizationService.class);
        RestClient client = mock(RestClient.class);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a member of this project"))
                .when(authorization).requireProjectViewer(99L);
        var service = new CopilotService(authorization, client, TOKEN);

        var exception = assertThrows(ResponseStatusException.class,
                () -> service.ask(new CopilotAskRequestDTO("Private data?", 99L)));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(client);
    }

    @Test
    void providerErrorsReturnSanitized503() {
        AuthorizationService authorization = mock(AuthorizationService.class);
        RestClient.Builder builder = RestClient.builder().baseUrl("http://rag.internal:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://rag.internal:8000/ask"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("provider-secret and internal host details").contentType(MediaType.TEXT_PLAIN));
        var service = new CopilotService(authorization, builder.build(), TOKEN);

        var exception = assertThrows(ResponseStatusException.class,
                () -> service.ask(new CopilotAskRequestDTO("What changed?", 12L)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        assertEquals("The project assistant is temporarily unavailable.", exception.getReason());
        server.verify();
    }

    @Test
    void malformedSuccessfulResponseReturns503() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://rag.internal:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://rag.internal:8000/ask"))
                .andRespond(withSuccess("{\"answer\":\"\",\"citations\":[]}", MediaType.APPLICATION_JSON));
        var service = new CopilotService(mock(AuthorizationService.class), builder.build(), TOKEN);

        var exception = assertThrows(ResponseStatusException.class,
                () -> service.ask(new CopilotAskRequestDTO("What changed?", 12L)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        server.verify();
    }

    @Test
    void missingServiceTokenDoesNotContactPython() {
        RestClient client = mock(RestClient.class);
        var service = new CopilotService(mock(AuthorizationService.class), client, "");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> service.ask(new CopilotAskRequestDTO("What changed?", 12L))).getStatusCode());
        verifyNoInteractions(client);
    }

    @Test
    void controlCharactersOnlyQuestionIsRejected() {
        RestClient client = mock(RestClient.class);
        var service = new CopilotService(mock(AuthorizationService.class), client, TOKEN);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.ask(new CopilotAskRequestDTO("\u0007\u0001", 12L))).getStatusCode());
        verifyNoInteractions(client);
    }
}
