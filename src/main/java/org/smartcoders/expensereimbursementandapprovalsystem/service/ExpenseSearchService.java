package org.smartcoders.expensereimbursementandapprovalsystem.service;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.ExpenseResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.ExpenseSearchCriteria;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.PageResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Expense;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.ExpenseStatus;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.User;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.ExpenseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExpenseSearchService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("expenseDate", "amount", "id", "status", "category");

    private final ExpenseRepository expenseRepository;
    private final ExpenseService expenseService;

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> search(String email, boolean privileged,
                                                ExpenseSearchCriteria criteria,
                                                int page, int size,
                                                String sortBy, String direction) {
        validate(criteria);

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                buildSort(sortBy, direction));

        Page<Expense> result =
                expenseRepository.findAll(buildSpec(email, privileged, criteria), pageable);

        return new PageResponse<>(
                result.getContent().stream().map(expenseService::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private void validate(ExpenseSearchCriteria c) {
        if (c.from() != null && c.to() != null && c.from().isAfter(c.to())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'from' date must not be after 'to' date");
        }
        if (c.minAmount() != null && c.maxAmount() != null
                && c.minAmount().compareTo(c.maxAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'minAmount' must not be greater than 'maxAmount'");
        }
    }

    private Sort buildSort(String sortBy, String direction) {
        String field = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "expenseDate";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Sort sort = Sort.by(dir, field);
        return field.equals("id") ? sort : sort.and(Sort.by(Sort.Direction.DESC, "id"));
    }

    private Specification<Expense> buildSpec(String email, boolean privileged,
                                             ExpenseSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Expense, User> user = root.join("user");

            // Employees can only ever see their own claims
            if (!privileged) {
                predicates.add(cb.equal(user.<String>get("email"), email));
            }

            ExpenseStatus status = c.status();
            if (status != null) {
                predicates.add(cb.equal(root.<ExpenseStatus>get("status"), status));
            }

            if (StringUtils.hasText(c.category())) {
                predicates.add(cb.equal(
                        cb.lower(root.<String>get("category")),
                        c.category().trim().toLowerCase()));
            }

            if (privileged && StringUtils.hasText(c.employee())) {
                String like = pattern(c.employee());
                predicates.add(cb.or(
                        cb.like(cb.lower(user.<String>get("name")), like, '\\'),
                        cb.like(cb.lower(user.<String>get("email")), like, '\\')));
            }

            LocalDate from = c.from();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("expenseDate"), from));
            }
            LocalDate to = c.to();
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("expenseDate"), to));
            }

            BigDecimal min = c.minAmount();
            if (min != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("amount"), min));
            }
            BigDecimal max = c.maxAmount();
            if (max != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("amount"), max));
            }

            if (StringUtils.hasText(c.keyword())) {
                String like = pattern(c.keyword());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("description")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("category")), like, '\\'),
                        cb.like(cb.lower(user.<String>get("name")), like, '\\')));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // Escapes % and _ so users cannot inject their own wildcards
    private String pattern(String value) {
        String escaped = value.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}