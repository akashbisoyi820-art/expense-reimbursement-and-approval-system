package org.smartcoders.expensereimbursementandapprovalsystem.service;

import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.config.JwtUtil;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.AuthResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.LoginRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.RegisterRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Role;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.User;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final DepartmentService departmentService;

    // Companies create accounts for their people, so public registration is off by default.
    // Set app.registration-open=true in application.properties to open it.
    @Value("${app.registration-open:false}")
    private boolean registrationOpen;

    public boolean isRegistrationOpen() {
        return registrationOpen;
    }

    // Public registration: always creates an EMPLOYEE, whatever role the request contains
    @Transactional
    public String register(RegisterRequest request) {
        if (!registrationOpen) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Registration is closed. Please ask an administrator to create your account.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already exists");
        }

        String department = departmentService.requireActiveName(request.getDepartment());

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.EMPLOYEE);
        user.setDepartment(department);
        user.setDesignation("Employee");
        user.setEmployeeId(departmentService.nextEmployeeId(department));
        user.setActive(true);
        user.setJoinedOn(LocalDate.now());

        userRepository.save(user);
        return "User registered successfully";
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This account has been deactivated. Please contact an administrator.");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(
                token,
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getEmployeeId(),
                user.getDepartment(),
                user.getDesignation());
    }
}