package com.ehtp.kanban_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;


import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sprints")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@BatchSize(size = 50)

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Sprint extends BaseAudit {
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @Column(name = "analysis_revision", nullable = false, columnDefinition = "bigint default 0")
    private long analysisRevision;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long sprintId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String objective;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "planned_points", nullable = false)
    private Integer plannedPoints = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    @Column(name = "is_concluded", nullable = false)
    private Boolean isConcluded = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "sprint_status", nullable = false)
    private SprintStatus sprintStatus = SprintStatus.PLANNED;

    public enum SprintStatus { PLANNED, ACTIVE, DONE }

    @JsonIgnoreProperties({"sprints", "requirements", "members", "owner"})
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Projet project;

    @JsonIgnoreProperties({"sprint", "subtasks"})
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "sprint")
    private List<Requirement> requirements = new ArrayList<>();

    @JsonIgnoreProperties({"sprint", "requirement", "decisionNotes", "statusHistory"})
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "sprint")
    private List<Task> tasks = new ArrayList<>();

    @JsonIgnoreProperties("sprint")
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "sprint", cascade = CascadeType.ALL)
    private List<AiInsight> aiInsights = new ArrayList<>();

    public Sprint() {}

    public void setSprintStatus(SprintStatus sprintStatus) {
        this.sprintStatus = sprintStatus;
        this.isActive = (sprintStatus == SprintStatus.ACTIVE);
        this.isConcluded = (sprintStatus == SprintStatus.DONE);
    }

    public Integer getPointsPlanned() {
        return plannedPoints;
    }

    public void setPointsPlanned(Integer pointsPlanned) {
        this.plannedPoints = pointsPlanned;
    }
}
