package org.smartcoders.expensereimbursementandapprovalsystem.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.PolicyChangeResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.PolicyRuleResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.PolicyUpdateRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.service.PolicyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/policy")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    // Everyone who is logged in can read the rules
    @GetMapping
    public ResponseEntity<List<PolicyRuleResponse>> getPolicy() {
        return ResponseEntity.ok(policyService.getAll());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PolicyRuleResponse>> update(@Valid @RequestBody PolicyUpdateRequest request,
                                                           Authentication authentication) {
        return ResponseEntity.ok(policyService.update(request, authentication.getName()));
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PolicyChangeResponse>> history() {
        return ResponseEntity.ok(policyService.getHistory());
    }
}