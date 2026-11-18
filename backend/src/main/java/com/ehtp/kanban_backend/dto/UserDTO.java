package com.ehtp.kanban_backend.dto;

import com.ehtp.kanban_backend.model.User;
import com.ehtp.kanban_backend.model.ProjectMembership;

public record UserDTO(
        Long id,
        String email,
        String fullName,
        User.Role role,
        Integer capacityPoints
) {
    public static UserDTO from(User user) {
        return new UserDTO(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getCapacityPoints()
        );
    }

    public static UserDTO from(User user, ProjectMembership.Role projectRole) {
        return new UserDTO(user.getId(), user.getEmail(), user.getFullName(),
                User.Role.valueOf(projectRole.name()), user.getCapacityPoints());
    }
}
