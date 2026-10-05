package org.smartcoders.expensereimbursementandapprovalsystem.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.AuthResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.LoginRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.RegisterRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}