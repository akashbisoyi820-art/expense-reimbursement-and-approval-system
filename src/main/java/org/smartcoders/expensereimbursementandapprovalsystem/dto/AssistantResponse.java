package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import java.util.List;

public record AssistantResponse(
        String answer,
        List<String> suggestions
) {
}