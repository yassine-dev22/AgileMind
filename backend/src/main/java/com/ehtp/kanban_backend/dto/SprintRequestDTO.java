package com.ehtp.kanban_backend.dto;

import com.ehtp.kanban_backend.model.Sprint;
import lombok.Data;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

@Data
public class SprintRequestDTO {
    @Size(max = 10000) private String objective;
    @Size(max = 10000) private String sprintObjective;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
    private Sprint.SprintStatus sprintStatus;
    @Min(0) private Integer pointsPlanned;
    @Min(0) private Integer plannedStoryPoints;
    @Positive private Long projectId;
    @Size(max = 1000) private List<@NotNull @Positive Long> requirementIds;

    public String getObjective() {
        return sprintObjective != null ? sprintObjective : objective;
    }

    public Integer getPointsPlanned() {
        return plannedStoryPoints != null ? plannedStoryPoints : pointsPlanned;
    }
}
