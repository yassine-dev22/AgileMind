package com.ehtp.kanban_backend.dto;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

@Data
public class AiRiskIngestionDTO {
    @NotNull @Min(0) @Max(100)
    private Integer sprintHealthScore;
    @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH")
    private String riskLevel;
    @Valid @Size(max = 100)
    private List<BottleneckAlert> bottleneckAlerts;
    @NotBlank @Size(max = 4000)
    private String automatedStandupSummary;

    @Data
    public static class BottleneckAlert {
        @NotBlank @Size(max = 255)
        private String issueId;
        @NotBlank @Size(max = 4000)
        private String warningMessage;
    }
}
