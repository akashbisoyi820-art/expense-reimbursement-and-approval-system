package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Role;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 6)
    private String password;

    // Ignored: public registration always creates an EMPLOYEE
    private Role role;

    @Size(max = 50)
    private String department;
}