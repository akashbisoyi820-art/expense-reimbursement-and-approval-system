package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import org.smartcoders.expensereimbursementandapprovalsystem.entity.Role;

import java.time.LocalDate;

public record UserResponse(
        Long id,
        String employeeId,
        String name,
        String email,
        Role role,
        String department,
        String designation,
        boolean active,
        Long reportingManagerId,
        String reportingManagerName,
        LocalDate joinedOn
) {
}