package com.ehtp.kanban_backend.dto;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class LoginRequest {
    @NotBlank @Email @Size(max = 254)
    private String email;
    @NotBlank @Size(max = 72)
    private String password;
}
