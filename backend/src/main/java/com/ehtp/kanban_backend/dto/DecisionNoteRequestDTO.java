package com.ehtp.kanban_backend.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class DecisionNoteRequestDTO {
    @NotBlank @Size(max = 2000) private String what;
    @NotBlank @Size(max = 2000) private String why;
    private Long decidedByUserId;
}
