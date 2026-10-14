package com.ehtp.kanban_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "subtasks")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@BatchSize(size = 50)

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Task extends BaseAudit {
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.TODO;

    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;

    @Column(nullable = false)
    private Integer effortPoints = 0;

    @Column(nullable = false)
    private Integer estimatedHours = 0;

    @Column(nullable = false)
    private LocalDateTime statusChangedAt;

    @JsonIgnoreProperties({"tasks", "aiInsights", "project", "requirements"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id")
    private Sprint sprint;

    @JsonIgnoreProperties({"tasks", "ownedProjects", "memberProjects"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignee;

    @JsonIgnoreProperties({"subtasks", "sprint", "project"})
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id", nullable = false)
    private Requirement requirement;

    @JsonIgnoreProperties("task")
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DecisionNote> decisionNotes = new ArrayList<>();

    @JsonIgnoreProperties("task")
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TaskStatusHistory> statusHistory = new ArrayList<>();

    public Task() {}

    @PrePersist
    public void onPersistTask() {
        if (statusChangedAt == null) {
            statusChangedAt = LocalDateTime.now();
        }
        if (isCompleted == null) {
            isCompleted = (status == TaskStatus.DONE);
        }
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
        this.isCompleted = (status == TaskStatus.DONE);
    }

    public void setIsCompleted(Boolean isCompleted) {
        this.isCompleted = isCompleted;
        if (Boolean.TRUE.equals(isCompleted)) {
            this.status = TaskStatus.DONE;
        } else if (this.status == TaskStatus.DONE) {
            this.status = TaskStatus.TODO;
        }
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, URGENT
    }

    public enum TaskStatus {
        TODO, IN_PROGRESS, DONE
    }
}
