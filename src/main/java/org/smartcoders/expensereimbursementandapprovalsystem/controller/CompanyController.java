package org.smartcoders.expensereimbursementandapprovalsystem.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.DepartmentRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.DepartmentResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.UserResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.service.DepartmentService;
import org.smartcoders.expensereimbursementandapprovalsystem.service.PeopleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CompanyController {

    private final DepartmentService departmentService;
    private final PeopleService peopleService;

    // Everyone who is logged in can read the departments and the directory
    @GetMapping("/departments")
    public ResponseEntity<List<DepartmentResponse>> departments() {
        return ResponseEntity.ok(departmentService.getAll());
    }

    @PostMapping("/departments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DepartmentResponse> createDepartment(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(departmentService.create(request));
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DepartmentResponse> updateDepartment(@PathVariable Long id,
                                                               @Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(departmentService.update(id, request));
    }

    @GetMapping("/directory")
    public ResponseEntity<List<UserResponse>> directory() {
        return ResponseEntity.ok(peopleService.directory());
    }
}