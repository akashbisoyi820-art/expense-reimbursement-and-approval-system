package org.smartcoders.expensereimbursementandapprovalsystem.service;

import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.config.JwtUtil;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.AuthResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.CreateUserRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.LoginRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.RegisterRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Role;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.User;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final List<String> DEPARTMENTS =
            List.of("Engineering", "Finance", "HR", "Sales", "Marketing", "Operations", "Other");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // Public registration: always creates an EMPLOYEE, whatever role the request contains
    public String register(RegisterRequest request) {
        return saveUser(
                request.getName(),
                request.getEmail(),
                request.getPassword(),
                Role.EMPLOYEE,
                request.getDepartment(),
                "User registered successfully");
    }

    // Admin-only: creates a user with the requested role
    public String createUser(CreateUserRequest request) {
        return saveUser(
                request.getName(),
                request.getEmail(),
                request.getPassword(),
                request.getRole(),
                request.getDepartment(),
                "User created successfully");
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, user.getName(), user.getEmail(), user.getRole().name());
    }

    private String saveUser(String name, String email, String password, Role role,
                            String department, String successMessage) {
        String normalizedDepartment = normalizeDepartment(department);

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already exists");
        }

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setDepartment(normalizedDepartment);

        userRepository.save(user);
        return successMessage;
    }

    private String normalizeDepartment(String department) {
        if (department == null || department.isBlank()) {
            return "Other";
        }
        return DEPARTMENTS.stream()
                .filter(d -> d.equalsIgnoreCase(department.trim()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid department. Allowed: " + String.join(", ", DEPARTMENTS)));
    }
}