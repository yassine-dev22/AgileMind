package com.ehtp.kanban_backend.dto;

import com.ehtp.kanban_backend.model.User;
import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

@Data
public class RegisterRequest {
    @NotBlank @Email @Size(max = 254)
    private String email;
    @NotBlank @Size(min = 6, max = 72)
    private String password;
    @Size(max = 255)
    private String fullName;
    @Size(max = 255)
    private String firstName;
    @Size(max = 255)
    private String lastName;
    private User.Role role;
    @Min(0) @Max(10000)
    private Integer capacityPoints;
}
