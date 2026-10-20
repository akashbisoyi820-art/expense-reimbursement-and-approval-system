package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssistantRequest {

    @NotBlank
    @Size(max = 300)
    private String message;
}