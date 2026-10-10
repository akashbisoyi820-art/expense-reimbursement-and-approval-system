package org.smartcoders.expensereimbursementandapprovalsystem.controller;

import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.AuditLogResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/recent")
    public ResponseEntity<List<AuditLogResponse>> recent() {
        return ResponseEntity.ok(auditService.getRecent());
    }
}