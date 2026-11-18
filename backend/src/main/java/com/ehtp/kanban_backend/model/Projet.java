package com.ehtp.kanban_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;


import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "projects")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@BatchSize(size = 50)

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Projet extends BaseAudit {
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    public enum ProjectStatus {
        PLANNED,
        IN_PROGRESS,
        DONE
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonIgnoreProperties({"ownedProjects", "memberProjects", "passwordHash"})
    private User owner;

    @BatchSize(size = 50)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "project_members",
        joinColumns = @JoinColumn(name = "project_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @JsonIgnoreProperties({"ownedProjects", "memberProjects", "passwordHash"})
    private List<User> members = new ArrayList<>();

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("project")
    private List<Requirement> requirements = new ArrayList<>();

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("project")
    private List<Sprint> sprints = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AiInsight> aiInsights = new ArrayList<>();

    @JsonIgnore
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjectMembership> memberships = new ArrayList<>();

    /** Persisted project roles take precedence; legacy members remain readable during migration. */
    public ProjectMembership.Role roleFor(User user) {
        if (user == null) return null;
        ProjectMembership membership = memberships.stream()
                .filter(entry -> entry.getUser().getId() == user.getId()).findFirst().orElse(null);
        return roleFor(user, membership == null ? null : membership.getRole());
    }

    public ProjectMembership.Role roleFor(User user, ProjectMembership.Role persistedRole) {
        if (user == null) return null;
        boolean actualOwner = owner != null && owner.getId() == user.getId();
        boolean legacyMember = members.stream().anyMatch(member -> member.getId() == user.getId());
        if (!actualOwner && persistedRole == null && !legacyMember) return null;
        if (user.getRole() == User.Role.VIEWER
                || persistedRole == ProjectMembership.Role.VIEWER) {
            return ProjectMembership.Role.VIEWER;
        }
        // A membership row alone cannot confer ownership of someone else's project.
        if (actualOwner) return ProjectMembership.Role.OWNER;
        return ProjectMembership.Role.MEMBER;
    }

    /** Keep the existing roster API while also including users stored only in project_memberships. */
    public List<User> roster() {
        Map<Long, User> users = new LinkedHashMap<>();
        if (owner != null) users.put(owner.getId(), owner);
        members.forEach(member -> users.put(member.getId(), member));
        memberships.forEach(entry -> users.put(entry.getUser().getId(), entry.getUser()));
        return new ArrayList<>(users.values());
    }

    public boolean canContribute(User user) {
        ProjectMembership.Role role = roleFor(user);
        return role == ProjectMembership.Role.OWNER || role == ProjectMembership.Role.MEMBER;
    }

    public Projet() {}
}
