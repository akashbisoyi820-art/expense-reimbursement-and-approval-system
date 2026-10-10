package org.smartcoders.expensereimbursementandapprovalsystem.service;

import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.AuditLogResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.AuditAction;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.AuditLog;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.ExpenseStatus;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.User;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(Long expenseId, AuditAction action, User actor,
                    ExpenseStatus from, ExpenseStatus to, String details) {
        AuditLog entry = new AuditLog();
        entry.setExpenseId(expenseId);
        entry.setAction(action);
        entry.setActorName(actor.getName());
        entry.setActorEmail(actor.getEmail());
        entry.setActorRole(actor.getRole().name());
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setDetails(details);
        auditLogRepository.save(entry);
    }

    public List<AuditLogResponse> getHistory(Long expenseId) {
        return auditLogRepository.findByExpenseIdOrderByCreatedAtAscIdAsc(expenseId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AuditLogResponse> getRecent() {
        return auditLogRepository.findTop200ByOrderByCreatedAtDescIdDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditLogResponse toResponse(AuditLog a) {
        return new AuditLogResponse(
                a.getId(),
                a.getExpenseId(),
                a.getAction(),
                a.getActorName(),
                a.getActorRole(),
                a.getFromStatus(),
                a.getToStatus(),
                a.getDetails(),
                a.getCreatedAt()
        );
    }
}