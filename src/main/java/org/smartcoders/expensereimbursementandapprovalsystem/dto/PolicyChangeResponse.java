package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import java.time.LocalDateTime;

public record PolicyChangeResponse(
        Long id,
        String summary,
        String changedByName,
        LocalDateTime createdAt
) {
}