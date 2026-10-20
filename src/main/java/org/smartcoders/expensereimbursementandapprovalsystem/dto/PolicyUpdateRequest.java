package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PolicyUpdateRequest {

    @NotEmpty
    @Valid
    private List<PolicyRuleRequest> rules;
}