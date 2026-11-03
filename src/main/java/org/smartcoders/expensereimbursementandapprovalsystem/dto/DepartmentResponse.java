package org.smartcoders.expensereimbursementandapprovalsystem.dto;

public record DepartmentResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean active,
        Long headId,
        String headName,
        long employeeCount
) {
}