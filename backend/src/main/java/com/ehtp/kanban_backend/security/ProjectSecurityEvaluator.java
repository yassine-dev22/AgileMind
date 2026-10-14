package com.ehtp.kanban_backend.security;

import com.ehtp.kanban_backend.model.ProjectMembership;
import com.ehtp.kanban_backend.service.AuthorizationService;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Component("projectSecurity")
public class ProjectSecurityEvaluator {
    private final AuthorizationService authorizationService;

    public ProjectSecurityEvaluator(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public boolean hasRole(Long projectId, String roleStr) {
        if (projectId == null || roleStr == null) return false;
        try {
            ProjectMembership.Role required = ProjectMembership.Role.valueOf(roleStr.toUpperCase(Locale.ROOT));
            ProjectMembership.Role actual = authorizationService.getProjectRole(projectId);
            if (actual == null) return false;
            return switch (required) {
                case OWNER -> actual == ProjectMembership.Role.OWNER;
                case MEMBER -> actual == ProjectMembership.Role.OWNER || actual == ProjectMembership.Role.MEMBER;
                case VIEWER -> true;
            };
        } catch (IllegalArgumentException | ResponseStatusException exception) {
            return false;
        }
    }
}
