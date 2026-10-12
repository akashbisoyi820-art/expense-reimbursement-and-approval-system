package org.smartcoders.expensereimbursementandapprovalsystem.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.AnalyticsResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.ChartPoint;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Expense;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.ExpenseStatus;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.ExpenseRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final int MAX_MONTHS = 12;

    private final ExpenseRepository expenseRepository;

    // The totals are grouped in memory, which is fine for the size of this project.
    // For very large data sets, move these groupings into GROUP BY queries.
    @Transactional(readOnly = true)
    public AnalyticsResponse summary(String email, boolean privileged,
                                     ExpenseStatus status, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'from' date must not be after 'to' date");
        }

        Specification<Expense> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Employees only ever see their own claims
            if (!privileged) {
                predicates.add(cb.equal(root.join("user").<String>get("email"), email));
            }
            if (status != null) {
                predicates.add(cb.equal(root.<ExpenseStatus>get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("expenseDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("expenseDate"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<Expense> claims = expenseRepository.findAll(spec);

        BigDecimal grandTotal = claims.stream()
                .map(this::amountOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AnalyticsResponse(
                ExchangeRateService.BASE_CURRENCY,
                grandTotal,
                claims.size(),
                group(claims, e -> blankTo(e.getCategory(), "Other")),
                byMonth(claims),
                group(claims, e -> blankTo(e.getUser().getDepartment(), "Unassigned")),
                group(claims, e -> e.getStatus().name())
        );
    }

    private List<ChartPoint> group(List<Expense> claims, Function<Expense, String> keyOf) {
        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, Long> counts = new HashMap<>();

        for (Expense e : claims) {
            String key = keyOf.apply(e);
            totals.merge(key, amountOf(e), BigDecimal::add);
            counts.merge(key, 1L, Long::sum);
        }

        return totals.entrySet().stream()
                .map(entry -> new ChartPoint(entry.getKey(), entry.getValue(), counts.get(entry.getKey())))
                .sorted(Comparator.comparing(ChartPoint::total).reversed())
                .toList();
    }

    // One point per month, with zero for months that have no claims
    private List<ChartPoint> byMonth(List<Expense> claims) {
        TreeMap<YearMonth, BigDecimal> totals = new TreeMap<>();
        Map<YearMonth, Long> counts = new HashMap<>();

        for (Expense e : claims) {
            if (e.getExpenseDate() == null) {
                continue;
            }
            YearMonth month = YearMonth.from(e.getExpenseDate());
            totals.merge(month, amountOf(e), BigDecimal::add);
            counts.merge(month, 1L, Long::sum);
        }

        if (totals.isEmpty()) {
            return List.of();
        }

        YearMonth last = totals.lastKey();
        YearMonth start = totals.firstKey();
        YearMonth earliest = last.minusMonths(MAX_MONTHS - 1);
        if (start.isBefore(earliest)) {
            start = earliest;
        }

        List<ChartPoint> points = new ArrayList<>();
        for (YearMonth month = start; !month.isAfter(last); month = month.plusMonths(1)) {
            points.add(new ChartPoint(
                    month.toString(),
                    totals.getOrDefault(month, BigDecimal.ZERO),
                    counts.getOrDefault(month, 0L)));
        }
        return points;
    }

    // Older claims have no converted amount: use their own amount (they were in INR)
    private BigDecimal amountOf(Expense e) {
        return e.getAmountInBase() != null ? e.getAmountInBase() : e.getAmount();
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}