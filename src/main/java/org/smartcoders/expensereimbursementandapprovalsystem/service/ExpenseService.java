package org.smartcoders.expensereimbursementandapprovalsystem.service;

import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.ExpenseRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.ExpenseResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Expense;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.ExpenseStatus;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.User;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.ExpenseRepository;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ReceiptStorageService receiptStorageService;

    public record ReceiptFile(Resource resource, String contentType) {}

    public ExpenseResponse createExpense(String email, ExpenseRequest request) {
        User user = getUser(email);

        Expense expense = new Expense();
        expense.setUser(user);
        expense.setCategory(request.getCategory());
        expense.setAmount(request.getAmount());
        expense.setExpenseDate(request.getExpenseDate());
        expense.setDescription(request.getDescription());
        expense.setStatus(ExpenseStatus.PENDING);

        return toResponse(expenseRepository.save(expense));
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

    public ExpenseResponse approve(Long id) {
        Expense expense = getExpense(id);
        requireStatus(expense, ExpenseStatus.PENDING);
        expense.setStatus(ExpenseStatus.APPROVED);
        return toResponse(expenseRepository.save(expense));
    }

    public ExpenseResponse reject(Long id, String reason) {
        Expense expense = getExpense(id);
        requireStatus(expense, ExpenseStatus.PENDING);
        expense.setStatus(ExpenseStatus.REJECTED);
        expense.setRejectionReason(reason);
        return toResponse(expenseRepository.save(expense));
    }

    public ExpenseResponse reimburse(Long id) {
        Expense expense = getExpense(id);
        requireStatus(expense, ExpenseStatus.APPROVED);
        expense.setStatus(ExpenseStatus.REIMBURSED);
        return toResponse(expenseRepository.save(expense));
    }

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
        return toResponse(expenseRepository.save(expense));
    }

    public ReceiptFile getReceipt(String email, boolean privileged, Long id) {
        Expense expense = getExpense(id);

        if (!privileged && !expense.getUser().getEmail().equals(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You cannot view this receipt");
        }
        if (!hasReceipt(expense)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No receipt uploaded");
        }

        String filename = expense.getReceiptPath();
        return new ReceiptFile(
                receiptStorageService.load(filename),
                receiptStorageService.contentTypeFor(filename));
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

    private ExpenseResponse toResponse(Expense e) {
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