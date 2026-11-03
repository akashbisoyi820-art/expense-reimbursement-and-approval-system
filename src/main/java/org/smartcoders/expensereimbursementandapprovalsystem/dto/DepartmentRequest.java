package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepartmentRequest {

    // Needed when creating a department; ignored when updating (the code never changes)
    @Pattern(regexp = "^[A-Za-z]{2,5}$", message = "The code must be 2 to 5 letters")
    private String code;

    // Needed when creating a department; ignored when updating
    @Size(max = 50)
    private String name;

    @Size(max = 255)
    private String description;

    // An empty head clears the head
    private Long headId;

    // When updating: false deactivates the department. Missing means no change.
    private Boolean active;
}