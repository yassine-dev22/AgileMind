package com.ehtp.kanban_backend.dto;

import lombok.Data;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

@Data
public class RequirementRequestDTO {
    @Size(max = 255) private String title;
    @Size(max = 100000) private String description;
    @Pattern(regexp = "#[0-9a-fA-F]{6}") private String color;
    @Positive private Long sprintId;
}
