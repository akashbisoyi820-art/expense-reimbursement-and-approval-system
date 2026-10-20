package org.smartcoders.expensereimbursementandapprovalsystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private String category;

    // Amount exactly as the employee entered it, in the claim's own currency
    @Column(nullable = false)
    private BigDecimal amount;

    // ISO code of the claim's currency, for example INR, USD, EUR
    @Column(length = 3)
    private String currency;

    // How many INR one unit of the currency was worth when the claim was submitted
    @Column(precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    // The claim amount converted to INR, using the rate saved above
    @Column(precision = 38, scale = 2)
    private BigDecimal amountInBase;

    // The date the exchange rate belongs to
    private LocalDate rateDate;

    private LocalDate expenseDate;

    private String description;

    private String receiptPath;

    @Enumerated(EnumType.STRING)
    private ExpenseStatus status = ExpenseStatus.PENDING;

    @Column(length = 500)
    private String rejectionReason;

    // Policy warnings saved when the claim was submitted (separated by ||)
    @Column(length = 1000)
    private String policyFlags;

    // Whether the policy asked for a receipt on this claim when it was submitted
    private Boolean receiptRequired;
}