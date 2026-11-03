package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Role;

@Getter
@Setter
public class CreateUserRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Email
    private String email;

    // The first password, set by the admin
    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    @NotNull
    private Role role;

    // The name of an active department. Missing means Other.
    @Size(max = 50)
    private String department;

    @Size(max = 80)
    private String designation;

    private Long reportingManagerId;
}