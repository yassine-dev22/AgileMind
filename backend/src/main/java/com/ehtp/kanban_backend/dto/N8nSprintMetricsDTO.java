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
    private Double workloadBalance;
    private Integer remainingDays;
    private List<BottleneckDTO> bottlenecks;
    private String activeSprintStatus;
    private List<Double> historicalVelocity;
    private List<ResourceWorkload> resourceWorkloads;

    @Data
    public static class ResourceWorkload {
        private Long userId;
        private String email;
        private String fullName;
        private Integer assignedPoints;
        private Integer capacityPoints;
        private Integer completedPoints;
        private Integer remainingPoints;
        private Double loadRatio;
        private boolean overCapacity;
        private boolean over120Percent;
    }
}
