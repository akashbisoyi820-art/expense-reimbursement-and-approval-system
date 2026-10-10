package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import org.smartcoders.expensereimbursementandapprovalsystem.entity.AuditAction;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.ExpenseStatus;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long expenseId,
        AuditAction action,
        String actorName,
        String actorRole,
        ExpenseStatus fromStatus,
        ExpenseStatus toStatus,
        String details,
        LocalDateTime createdAt
) {
}