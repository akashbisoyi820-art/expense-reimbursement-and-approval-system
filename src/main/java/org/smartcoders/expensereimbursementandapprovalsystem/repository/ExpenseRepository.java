package org.smartcoders.expensereimbursementandapprovalsystem.repository;

import org.smartcoders.expensereimbursementandapprovalsystem.entity.Expense;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.ExpenseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ExpenseRepository
        extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    List<Expense> findByUserId(Long userId);

    List<Expense> findByStatus(ExpenseStatus status);
}