package com.ehtp.kanban_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;


import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "requirements")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@BatchSize(size = 50)

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Requirement extends BaseAudit {
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "story_points")
    private Integer storyPoints = 0;

    @Column(length = 20)
    private String color; // Keep for UI compatibility

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequirementStatus status = RequirementStatus.BACKLOG;

    public enum RequirementStatus {
        BACKLOG, TODO, IN_PROGRESS, DONE
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnoreProperties({"requirements", "sprints", "members", "owner"})
    private Projet project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id", nullable = true)
    @JsonIgnoreProperties({"requirements", "project", "tasks", "aiInsights"})
    private Sprint sprint;

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "requirement", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("requirement")
    private List<Task> subtasks = new ArrayList<>();

    public Requirement() {}
}
