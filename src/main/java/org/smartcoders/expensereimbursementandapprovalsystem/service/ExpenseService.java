package org.smartcoders.expensereimbursementandapprovalsystem.service;

import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.AuditLogResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.ExpenseRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.ExpenseResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.AuditAction;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Expense;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.ExpenseStatus;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.User;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.ExpenseRepository;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ReceiptStorageService receiptStorageService;
    private final AuditService auditService;

    public record ReceiptFile(Resource resource, String contentType) {}

    @Transactional
    public ExpenseResponse createExpense(String email, ExpenseRequest request) {
        User user = getUser(email);

        Expense expense = new Expense();
        expense.setUser(user);
        expense.setCategory(request.getCategory());
        expense.setAmount(request.getAmount());
        expense.setExpenseDate(request.getExpenseDate());
        expense.setDescription(request.getDescription());
        expense.setStatus(ExpenseStatus.PENDING);

        Expense saved = expenseRepository.save(expense);
        auditService.log(saved.getId(), AuditAction.SUBMITTED, user,
                null, ExpenseStatus.PENDING,
                "Submitted a " + saved.getCategory() + " claim of " + saved.getAmount());
        return toResponse(saved);
    }

    public List<ExpenseResponse> getMyExpenses(String email) {
        User user = getUser(email);
        return expenseRepository.findByUserId(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ExpenseResponse> getPendingExpenses() {
        return expenseRepository.findByStatus(ExpenseStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ExpenseResponse> getApprovedExpenses() {
        return expenseRepository.findByStatus(ExpenseStatus.APPROVED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ExpenseResponse approve(Long id, String actorEmail) {
        User actor = getUser(actorEmail);
        Expense expense = getExpense(id);
        requireStatus(expense, ExpenseStatus.PENDING);

        ExpenseStatus from = expense.getStatus();
        expense.setStatus(ExpenseStatus.APPROVED);
        Expense saved = expenseRepository.save(expense);

        auditService.log(id, AuditAction.APPROVED, actor, from, ExpenseStatus.APPROVED, null);
        return toResponse(saved);
    }

    @Transactional
    public ExpenseResponse reject(Long id, String reason, String actorEmail) {
        User actor = getUser(actorEmail);
        Expense expense = getExpense(id);
        requireStatus(expense, ExpenseStatus.PENDING);

        ExpenseStatus from = expense.getStatus();
        expense.setStatus(ExpenseStatus.REJECTED);
        expense.setRejectionReason(reason);
        Expense saved = expenseRepository.save(expense);

        auditService.log(id, AuditAction.REJECTED, actor, from, ExpenseStatus.REJECTED, reason);
        return toResponse(saved);
    }

    @Transactional
    public ExpenseResponse reimburse(Long id, String actorEmail) {
        User actor = getUser(actorEmail);
        Expense expense = getExpense(id);
        requireStatus(expense, ExpenseStatus.APPROVED);

        ExpenseStatus from = expense.getStatus();
        expense.setStatus(ExpenseStatus.REIMBURSED);
        Expense saved = expenseRepository.save(expense);

        auditService.log(id, AuditAction.REIMBURSED, actor, from, ExpenseStatus.REIMBURSED, null);
        return toResponse(saved);
    }

    @Transactional
    public ExpenseResponse uploadReceipt(String email, Long id, MultipartFile file) {
        Expense expense = getExpense(id);

        if (!expense.getUser().getEmail().equals(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only upload receipts for your own expenses");
        }
        requireStatus(expense, ExpenseStatus.PENDING);
        if (hasReceipt(expense)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt already uploaded");
        }

        expense.setReceiptPath(receiptStorageService.store(file));
        Expense saved = expenseRepository.save(expense);

        auditService.log(id, AuditAction.RECEIPT_UPLOADED, expense.getUser(),
                expense.getStatus(), expense.getStatus(), "Receipt uploaded");
        return toResponse(saved);
    }

    public ReceiptFile getReceipt(String email, boolean privileged, Long id) {
        Expense expense = requireAccessibleExpense(id, email, privileged);

        if (!hasReceipt(expense)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No receipt uploaded");
        }

        String filename = expense.getReceiptPath();
        return new ReceiptFile(
                receiptStorageService.load(filename),
                receiptStorageService.contentTypeFor(filename));
    }

    public List<AuditLogResponse> getHistory(String email, boolean privileged, Long id) {
        requireAccessibleExpense(id, email, privileged);
        return auditService.getHistory(id);
    }

    // Owner, managers and admins can access a claim
    public Expense requireAccessibleExpense(Long id, String email, boolean privileged) {
        Expense expense = getExpense(id);
        if (!privileged && !expense.getUser().getEmail().equals(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this claim");
        }
        return expense;
    }

    private boolean hasReceipt(Expense expense) {
        return expense.getReceiptPath() != null && !expense.getReceiptPath().isBlank();
    }

    private Expense getExpense(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
    }

    private void requireStatus(Expense expense, ExpenseStatus required) {
        if (expense.getStatus() != required) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Expense must be " + required + " but is " + expense.getStatus());
        }
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public ExpenseResponse toResponse(Expense e) {
        return new ExpenseResponse(
                e.getId(),
                e.getCategory(),
                e.getAmount(),
                e.getExpenseDate(),
                e.getDescription(),
                e.getStatus(),
                e.getUser().getName(),
                e.getUser().getEmail(),
                e.getRejectionReason(),
                hasReceipt(e)
        );
    }
}