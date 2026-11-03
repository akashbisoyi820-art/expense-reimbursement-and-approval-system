package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Role;

@Getter
@Setter
public class UserUpdateRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private Role role;

    @Size(max = 50)
    private String department;

    @Size(max = 80)
    private String designation;

    // An empty value clears the reporting manager
    private Long reportingManagerId;

    @NotNull
    private Boolean active;
}