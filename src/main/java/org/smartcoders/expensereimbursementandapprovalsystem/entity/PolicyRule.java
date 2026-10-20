package org.smartcoders.expensereimbursementandapprovalsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "policy_rules", uniqueConstraints = @UniqueConstraint(columnNames = "category"))
@Getter
@Setter
@NoArgsConstructor
public class PolicyRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String category;

    // All amounts are in INR. A missing value (null) means "no such rule".

    // A single claim above this amount is flagged
    @Column(precision = 38, scale = 2)
    private BigDecimal maxPerClaim;

    // One employee's claims in one month for this category above this amount are flagged
    @Column(precision = 38, scale = 2)
    private BigDecimal monthlyLimit;

    // A receipt is required for claims above this amount (0 means always)
    @Column(precision = 38, scale = 2)
    private BigDecimal receiptRequiredAbove;

    private LocalDateTime updatedAt;

    private String updatedByName;
}