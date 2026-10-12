package org.smartcoders.expensereimbursementandapprovalsystem.service;

import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.PaymentRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.PaymentResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Expense;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Payment;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.PaymentMethod;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.User;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    // Validates the request and saves the payment. Called inside the reimburse transaction.
    public Payment record(Expense expense, PaymentRequest request, User actor) {
        PaymentMethod method = request.getPaymentMethod();
        String reference = request.getReferenceNo() == null ? "" : request.getReferenceNo().trim();

        if (method != PaymentMethod.CASH && reference.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A reference number is required for " + label(method) + " payments");
        }

        LocalDate today = LocalDate.now();
        LocalDate paidOn = request.getPaidOn() != null ? request.getPaidOn() : today;

        if (paidOn.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment date cannot be in the future");
        }
        if (expense.getExpenseDate() != null && paidOn.isBefore(expense.getExpenseDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment date cannot be before the expense date");
        }
        if (paymentRepository.findByExpenseId(expense.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A payment is already recorded for this claim");
        }

        BigDecimal amount = expense.getAmountInBase() != null
                ? expense.getAmountInBase()
                : expense.getAmount();

        String note = request.getNote() == null ? "" : request.getNote().trim();

        Payment payment = new Payment();
        payment.setExpenseId(expense.getId());
        payment.setPaymentMethod(method);
        payment.setReferenceNo(reference.isEmpty() ? null : reference);
        payment.setPaidOn(paidOn);
        payment.setAmount(amount);
        payment.setNote(note.isEmpty() ? null : note);
        payment.setPaidByName(actor.getName());
        payment.setPaidByEmail(actor.getEmail());

        return paymentRepository.save(payment);
    }

    public Optional<PaymentResponse> getForExpense(Long expenseId) {
        return paymentRepository.findByExpenseId(expenseId).map(this::toResponse);
    }

    // One-line summary for the audit trail
    public String describe(Payment p) {
        StringBuilder text = new StringBuilder("Paid by ").append(label(p.getPaymentMethod()));
        if (p.getReferenceNo() != null) {
            text.append(", ref ").append(p.getReferenceNo());
        }
        text.append(", on ").append(p.getPaidOn())
                .append(" (").append(ExchangeRateService.BASE_CURRENCY).append(' ')
                .append(p.getAmount()).append(')');
        return text.toString();
    }

    private String label(PaymentMethod method) {
        return method.name().replace('_', ' ');
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getExpenseId(),
                p.getPaymentMethod(),
                p.getReferenceNo(),
                p.getPaidOn(),
                p.getAmount(),
                p.getNote(),
                p.getPaidByName(),
                p.getCreatedAt()
        );
    }
}