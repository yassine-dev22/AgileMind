package com.ehtp.kanban_backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class N8nSprintMetricsDTO {
    private Long projectId;
    private Long sprintId;
    private Long analysisRevision;
    private String sprintName;
    private Integer plannedPoints;
    private Integer completedPoints;
    private Double velocityRatio;
    private Integer healthScore;
    private String activeSprintStatus;
    private List<Double> historicalVelocity;
    private List<ResourceWorkload> resourceWorkloads;

    @Data
    public static class ResourceWorkload {
        private Long userId;
        private String email;
        private Integer assignedPoints;
        private Integer capacityPoints;
    }
}
