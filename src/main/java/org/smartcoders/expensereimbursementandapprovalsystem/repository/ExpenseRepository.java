package org.smartcoders.expensereimbursementandapprovalsystem.repository;

import org.smartcoders.expensereimbursementandapprovalsystem.entity.Expense;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.ExpenseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository
        extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    List<Expense> findByUserId(Long userId);

    List<Expense> findByStatus(ExpenseStatus status);

    // INR total of one employee's claims in a category within a date range.
    // Returns null when there are no matching claims.
    @Query("select sum(coalesce(e.amountInBase, e.amount)) from Expense e "
            + "where e.user.id = :userId and lower(e.category) = lower(:category) "
            + "and e.expenseDate between :monthStart and :monthEnd "
            + "and e.status <> :excluded")
    BigDecimal sumInBase(@Param("userId") Long userId,
                         @Param("category") String category,
                         @Param("monthStart") LocalDate monthStart,
                         @Param("monthEnd") LocalDate monthEnd,
                         @Param("excluded") ExpenseStatus excluded);
}