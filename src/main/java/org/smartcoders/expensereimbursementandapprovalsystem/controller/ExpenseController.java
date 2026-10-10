package org.smartcoders.expensereimbursementandapprovalsystem.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.AuditLogResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.CommentRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.CommentResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.ExpenseRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.ExpenseResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.RejectRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.service.CommentService;
import org.smartcoders.expensereimbursementandapprovalsystem.service.ExpenseService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(@Valid @RequestBody ExpenseRequest request,
                                                         Authentication authentication) {
        return ResponseEntity.ok(expenseService.createExpense(authentication.getName(), request));
    }

    @GetMapping("/my")
    public ResponseEntity<List<ExpenseResponse>> getMyExpenses(Authentication authentication) {
        return ResponseEntity.ok(expenseService.getMyExpenses(authentication.getName()));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<List<ExpenseResponse>> getPendingExpenses() {
        return ResponseEntity.ok(expenseService.getPendingExpenses());
    }

    @GetMapping("/approved")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ExpenseResponse>> getApprovedExpenses() {
        return ResponseEntity.ok(expenseService.getApprovedExpenses());
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ExpenseResponse> approve(@PathVariable Long id,
                                                   Authentication authentication) {
        return ResponseEntity.ok(expenseService.approve(id, authentication.getName()));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ExpenseResponse> reject(@PathVariable Long id,
                                                  @Valid @RequestBody RejectRequest request,
                                                  Authentication authentication) {
        return ResponseEntity.ok(expenseService.reject(id, request.getReason(), authentication.getName()));
    }

    @PutMapping("/{id}/reimburse")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExpenseResponse> reimburse(@PathVariable Long id,
                                                     Authentication authentication) {
        return ResponseEntity.ok(expenseService.reimburse(id, authentication.getName()));
    }

    @PostMapping(value = "/{id}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExpenseResponse> uploadReceipt(@PathVariable Long id,
                                                         @RequestParam("file") MultipartFile file,
                                                         Authentication authentication) {
        return ResponseEntity.ok(expenseService.uploadReceipt(authentication.getName(), id, file));
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<Resource> downloadReceipt(@PathVariable Long id,
                                                    Authentication authentication) {
        ExpenseService.ReceiptFile receipt =
                expenseService.getReceipt(authentication.getName(), isPrivileged(authentication), id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(receipt.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(receipt.resource());
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<AuditLogResponse>> getHistory(@PathVariable Long id,
                                                             Authentication authentication) {
        return ResponseEntity.ok(
                expenseService.getHistory(authentication.getName(), isPrivileged(authentication), id));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable Long id,
                                                             Authentication authentication) {
        return ResponseEntity.ok(
                commentService.getComments(id, authentication.getName(), isPrivileged(authentication)));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> addComment(@PathVariable Long id,
                                                      @Valid @RequestBody CommentRequest request,
                                                      Authentication authentication) {
        return ResponseEntity.ok(
                commentService.addComment(id, authentication.getName(),
                        isPrivileged(authentication), request.getMessage()));
    }

    private boolean isPrivileged(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER")
                        || a.getAuthority().equals("ROLE_ADMIN"));
    }
}