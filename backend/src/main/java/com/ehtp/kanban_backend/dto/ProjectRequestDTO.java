package com.ehtp.kanban_backend.dto;

import com.ehtp.kanban_backend.model.Projet;
import lombok.Data;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@Data
public class ProjectRequestDTO {
    @NotBlank @Size(max = 255) private String name;
    @Size(max = 100000) private String description;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
    private Projet.ProjectStatus status;
}
