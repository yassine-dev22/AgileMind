package com.ehtp.kanban_backend.service;

import com.ehtp.kanban_backend.dto.AuthResponse;
import com.ehtp.kanban_backend.dto.LoginRequest;
import com.ehtp.kanban_backend.dto.RegisterRequest;
import com.ehtp.kanban_backend.dto.UserDTO;
import com.ehtp.kanban_backend.model.User;
import com.ehtp.kanban_backend.repository.UserRepository;
import com.ehtp.kanban_backend.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");
        }
        validatePasswordBytes(request.getPassword());
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(resolveFullName(request));
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(User.Role.MEMBER);
        user.setCapacityPoints(request.getCapacityPoints() == null ? 40 : request.getCapacityPoints());

        User saved = userRepository.saveAndFlush(user);
        return new AuthResponse(jwtService.generateToken(saved), UserDTO.from(saved));
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase(Locale.ROOT);
        validatePasswordBytes(request.getPassword());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return new AuthResponse(jwtService.generateToken(user), UserDTO.from(user));
    }

    private String resolveFullName(RegisterRequest request) {
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            return request.getFullName().trim();
        }
        String firstName = request.getFirstName() == null ? "" : request.getFirstName().trim();
        String lastName = request.getLastName() == null ? "" : request.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        if (fullName.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full name must not exceed 255 characters");
        }
        return fullName.isBlank() ? request.getEmail().trim() : fullName;
    }

    private void validatePasswordBytes(String password) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        }
    }
}
