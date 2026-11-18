package com.ehtp.kanban_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CopilotAnswerDTO(String answer, List<Citation> citations) {
    public record Citation(
            @JsonProperty("source_type") String sourceType,
            @JsonProperty("source_id") Long sourceId,
            String author,
            String date,
            @JsonProperty("chunk_index") Integer chunkIndex
    ) {}
}
