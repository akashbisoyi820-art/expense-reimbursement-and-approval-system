package org.smartcoders.expensereimbursementandapprovalsystem.repository;

import org.smartcoders.expensereimbursementandapprovalsystem.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByExpenseIdOrderByCreatedAtAscIdAsc(Long expenseId);

    List<AuditLog> findTop200ByOrderByCreatedAtDescIdDesc();
}