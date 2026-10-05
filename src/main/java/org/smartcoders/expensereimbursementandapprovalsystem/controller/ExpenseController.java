package org.smartcoders.expensereimbursementandapprovalsystem.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.ExpenseRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.ExpenseResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.RejectRequest;
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
    public ResponseEntity<ExpenseResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.approve(id));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ExpenseResponse> reject(@PathVariable Long id,
                                                  @Valid @RequestBody RejectRequest request) {
        return ResponseEntity.ok(expenseService.reject(id, request.getReason()));
    }

    @PutMapping("/{id}/reimburse")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExpenseResponse> reimburse(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.reimburse(id));
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
        boolean privileged = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER")
                        || a.getAuthority().equals("ROLE_ADMIN"));

        ExpenseService.ReceiptFile receipt =
                expenseService.getReceipt(authentication.getName(), privileged, id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(receipt.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(receipt.resource());
    }
}