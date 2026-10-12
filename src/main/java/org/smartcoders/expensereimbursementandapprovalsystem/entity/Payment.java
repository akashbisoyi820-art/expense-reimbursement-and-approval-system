package org.smartcoders.expensereimbursementandapprovalsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(columnNames = "expense_id"))
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // One payment per claim
    @Column(name = "expense_id", nullable = false)
    private Long expenseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    // Transaction id, UTR or cheque number. Never a bank account number.
    @Column(length = 100)
    private String referenceNo;

    @Column(nullable = false)
    private LocalDate paidOn;

    // The INR amount that was paid out
    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String note;

    private String paidByName;
    private String paidByEmail;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}