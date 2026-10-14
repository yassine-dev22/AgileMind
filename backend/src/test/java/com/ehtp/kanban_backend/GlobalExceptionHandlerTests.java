package com.ehtp.kanban_backend;

import com.ehtp.kanban_backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTests {
    @Test
    void unexpectedFailuresDoNotExposeInternalExceptionMessages() {
        var result = new GlobalExceptionHandler().handleGeneralException(new RuntimeException("private-database-details"));
        assertEquals(500, result.getStatusCode().value());
        assertEquals(Map.of("error", "Internal Server Error"), result.getBody());
    }
}
