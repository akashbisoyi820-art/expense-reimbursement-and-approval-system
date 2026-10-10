package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        String authorName,
        String authorRole,
        String message,
        LocalDateTime createdAt,
        boolean mine
) {
}