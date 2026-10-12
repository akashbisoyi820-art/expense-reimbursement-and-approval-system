package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.PaymentMethod;

import java.time.LocalDate;

@Getter
@Setter
public class PaymentRequest {

    @NotNull
    private PaymentMethod paymentMethod;

    // Required for every method except CASH (checked in PaymentService)
    @Size(max = 60)
    @Pattern(regexp = "^[A-Za-z0-9 /_.\\-]*$",
            message = "Reference may contain only letters, digits, spaces and / _ . -")
    private String referenceNo;

    // Optional: today is used when missing
    private LocalDate paidOn;

    @Size(max = 255)
    private String note;
}