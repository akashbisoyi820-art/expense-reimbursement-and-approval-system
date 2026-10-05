package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectRequest {

    @NotBlank
    @Size(max = 500)
    private String reason;
}