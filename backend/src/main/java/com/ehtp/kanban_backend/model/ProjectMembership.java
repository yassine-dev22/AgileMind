package com.ehtp.kanban_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;


@Entity
@Table(name = "project_memberships", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "project_id"})
})
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@BatchSize(size = 50)

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ProjectMembership extends BaseAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"memberships", "tasks", "passwordHash"})
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnoreProperties("memberships")
    private Projet project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    public enum Role {
        OWNER, MEMBER, VIEWER
    }

    public ProjectMembership() {}

    public ProjectMembership(User user, Projet project, Role role) {
        this.user = user;
        this.project = project;
        this.role = role;
    }
}
