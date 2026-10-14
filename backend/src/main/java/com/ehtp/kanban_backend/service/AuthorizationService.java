package com.ehtp.kanban_backend.service;

import com.ehtp.kanban_backend.model.ProjectMembership;
import com.ehtp.kanban_backend.model.Projet;
import com.ehtp.kanban_backend.model.User;
import com.ehtp.kanban_backend.repository.ProjetRepository;
import com.ehtp.kanban_backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AuthorizationService {
    private final UserRepository userRepository;
    private final ProjetRepository projetRepository;

    public AuthorizationService(UserRepository userRepository, ProjetRepository projetRepository) {
        this.userRepository = userRepository;
        this.projetRepository = projetRepository;
    }

    public User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    public ProjectMembership.Role getProjectRole(Long projectId) {
        User user = currentUser();
        return getProjectRole(projectId, user);
    }

    private ProjectMembership.Role getProjectRole(Long projectId, User user) {
        if (projectId == null) return null;
        Projet project = projetRepository.findById(projectId).orElse(null);
        if (project == null) return null;
        if (project.getOwner() != null && project.getOwner().getId() == user.getId()) {
            return user.getRole() == User.Role.VIEWER ? ProjectMembership.Role.VIEWER : ProjectMembership.Role.OWNER;
        }
        if (project.getMembers() != null && project.getMembers().stream().anyMatch(m -> m.getId() == user.getId())) {
            return user.getRole() == User.Role.VIEWER ? ProjectMembership.Role.VIEWER : ProjectMembership.Role.MEMBER;
        }
        return null;
    }

    public User requireProjectOwner(Long projectId) {
        User user = currentUser();
        ProjectMembership.Role role = getProjectRole(projectId, user);
        if (role != ProjectMembership.Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Project OWNER can perform this action");
        }
        return user;
    }

    public User requireProjectEditor(Long projectId) {
        User user = currentUser();
        ProjectMembership.Role role = getProjectRole(projectId, user);
        if (role != ProjectMembership.Role.OWNER && role != ProjectMembership.Role.MEMBER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only OWNER or MEMBER can perform this action");
        }
        return user;
    }

    public User requireProjectViewer(Long projectId) {
        User user = currentUser();
        ProjectMembership.Role role = getProjectRole(projectId, user);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a member of this project");
        }
        return user;
    }

    public User requireProjectEditor(Projet project) {
        return requireProjectEditor(project.getId());
    }

    public User requireOwner() {
        User user = currentUser();
        if (user.getRole() != User.Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only OWNER can perform this action");
        }
        return user;
    }
}
