package com.ehtp.kanban_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;


@Entity
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@BatchSize(size = 50)

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class DecisionNote extends BaseAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, length = 2000)
    private String what;

    @Column(nullable = false, length = 2000)
    private String why;

    @JsonIgnoreProperties({"decisionNotes", "statusHistory", "requirement", "sprint", "assignee"})
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @JsonIgnoreProperties({"tasks", "projects"})
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
