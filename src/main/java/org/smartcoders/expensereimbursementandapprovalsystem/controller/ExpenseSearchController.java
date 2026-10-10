package org.smartcoders.expensereimbursementandapprovalsystem.controller;

import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.ExpenseResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.ExpenseSearchCriteria;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.PageResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.ExpenseStatus;
import org.smartcoders.expensereimbursementandapprovalsystem.service.ExpenseSearchService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/expenses/search")
@RequiredArgsConstructor
public class ExpenseSearchController {

    private final ExpenseSearchService expenseSearchService;

    @GetMapping
    public ResponseEntity<PageResponse<ExpenseResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ExpenseStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String employee,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "expenseDate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            Authentication authentication) {

        ExpenseSearchCriteria criteria = new ExpenseSearchCriteria(
                keyword, status, category, employee, from, to, minAmount, maxAmount);

        return ResponseEntity.ok(expenseSearchService.search(
                authentication.getName(), isPrivileged(authentication),
                criteria, page, size, sortBy, direction));
    }

    private boolean isPrivileged(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER")
                        || a.getAuthority().equals("ROLE_ADMIN"));
    }
}