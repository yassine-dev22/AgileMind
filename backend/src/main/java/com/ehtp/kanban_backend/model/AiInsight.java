package com.ehtp.kanban_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "ai_insights")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AiInsight extends BaseAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(length = 4000)
    private String explanation;

    private Integer confidenceScore;

    private Boolean acknowledged = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private InsightType insightType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id")
    @JsonIgnoreProperties({"aiInsights", "tasks", "requirements"})
    private Sprint sprint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    @JsonIgnoreProperties({"sprints", "requirements", "members", "owner"})
    private Projet project;

    private Integer sprintHealthScore;
    
    private String riskLevel;
    
    @Column(columnDefinition = "TEXT")
    private String bottlenecksJson;
    
    @Column(columnDefinition = "TEXT")
    private String automatedStandupSummary;

    public enum InsightType {
        RISK_ALERT,
        REASSIGNMENT_SUGGESTION,
        PATTERN_ANALYSIS
    }
}
