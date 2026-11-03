package org.smartcoders.expensereimbursementandapprovalsystem.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.CreateUserRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.PasswordResetRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.UserResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.UserUpdateRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Role;
import org.smartcoders.expensereimbursementandapprovalsystem.service.PeopleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final PeopleService peopleService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(peopleService.list(search, department, role, active));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(peopleService.get(id));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(peopleService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UserUpdateRequest request,
                                               Authentication authentication) {
        return ResponseEntity.ok(peopleService.update(id, request, authentication.getName()));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<String> resetPassword(@PathVariable Long id,
                                                @Valid @RequestBody PasswordResetRequest request) {
        peopleService.resetPassword(id, request.getPassword());
        return ResponseEntity.ok("Password has been reset");
    }
}