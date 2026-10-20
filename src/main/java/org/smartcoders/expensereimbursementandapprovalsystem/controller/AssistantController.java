package org.smartcoders.expensereimbursementandapprovalsystem.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.AssistantRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.AssistantResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.service.AssistantService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    @PostMapping("/ask")
    public ResponseEntity<AssistantResponse> ask(@Valid @RequestBody AssistantRequest request,
                                                 Authentication authentication) {
        boolean admin = hasRole(authentication, "ROLE_ADMIN");
        boolean privileged = admin || hasRole(authentication, "ROLE_MANAGER");

        return ResponseEntity.ok(assistantService.ask(
                authentication.getName(), privileged, admin, request.getMessage()));
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }
}