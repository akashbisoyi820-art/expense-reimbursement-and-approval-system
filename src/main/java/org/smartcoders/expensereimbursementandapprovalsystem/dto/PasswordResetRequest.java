package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResetRequest {

    @NotBlank
    @Size(min = 6, max = 100)
    private String password;
}