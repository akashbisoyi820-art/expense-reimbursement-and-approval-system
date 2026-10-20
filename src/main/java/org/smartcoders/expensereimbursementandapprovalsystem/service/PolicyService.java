package org.smartcoders.expensereimbursementandapprovalsystem.service;

import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.PolicyChangeResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.PolicyRuleRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.PolicyRuleResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.PolicyUpdateRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.ExpenseStatus;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.PolicyChange;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.PolicyRule;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.User;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.ExpenseRepository;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.PolicyChangeRepository;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.PolicyRuleRepository;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PolicyService {

    // Separates the saved policy warnings of one claim
    public static final String FLAG_SEPARATOR = "||";

    private static final String CURRENCY = ExchangeRateService.BASE_CURRENCY;

    // The result of checking one claim against the policy
    public record PolicyCheck(List<String> flags, boolean receiptRequired) {}

    private final PolicyRuleRepository ruleRepository;
    private final PolicyChangeRepository changeRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    // First start only: create a sensible default rule for every category
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDefaults() {
        if (ruleRepository.count() > 0) {
            return;
        }
        seed("Travel", 10000, 30000, 500);
        seed("Food", 2000, 8000, 300);
        seed("Accommodation", 15000, 45000, 0);
        seed("Office Supplies", 5000, 15000, 1000);
        seed("Other", 3000, 10000, 500);
    }

    private void seed(String category, long maxPerClaim, long monthlyLimit, long receiptAbove) {
        PolicyRule rule = new PolicyRule();
        rule.setCategory(category);
        rule.setMaxPerClaim(BigDecimal.valueOf(maxPerClaim));
        rule.setMonthlyLimit(BigDecimal.valueOf(monthlyLimit));
        rule.setReceiptRequiredAbove(BigDecimal.valueOf(receiptAbove));
        ruleRepository.save(rule);
    }

    public List<PolicyRuleResponse> getAll() {
        return ruleRepository.findAllByOrderByIdAsc().stream().map(this::toResponse).toList();
    }

    public List<PolicyChangeResponse> getHistory() {
        return changeRepository.findTop50ByOrderByCreatedAtDescIdDesc().stream()
                .map(c -> new PolicyChangeResponse(
                        c.getId(), c.getSummary(), c.getChangedByName(), c.getCreatedAt()))
                .toList();
    }

    // Only the categories in the request are changed. Either every row is accepted or none.
    @Transactional
    public List<PolicyRuleResponse> update(PolicyUpdateRequest request, String actorEmail) {
        User actor = userRepository.findByEmail(actorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Check every row first, so a bad row changes nothing
        for (PolicyRuleRequest r : request.getRules()) {
            if (ruleRepository.findByCategoryIgnoreCase(r.getCategory().trim()).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown category '" + r.getCategory() + "'");
            }
            if (r.getMaxPerClaim() != null && r.getMonthlyLimit() != null
                    && r.getMonthlyLimit().compareTo(r.getMaxPerClaim()) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "For " + r.getCategory() + ", the monthly limit cannot be below the per-claim limit");
            }
        }

        for (PolicyRuleRequest r : request.getRules()) {
            PolicyRule rule = ruleRepository.findByCategoryIgnoreCase(r.getCategory().trim()).orElseThrow();

            List<String> changes = new ArrayList<>();
            describe(changes, "per-claim limit", rule.getMaxPerClaim(), r.getMaxPerClaim());
            describe(changes, "monthly limit", rule.getMonthlyLimit(), r.getMonthlyLimit());
            describe(changes, "receipt required above",
                    rule.getReceiptRequiredAbove(), r.getReceiptRequiredAbove());

            if (changes.isEmpty()) {
                continue;
            }

            rule.setMaxPerClaim(r.getMaxPerClaim());
            rule.setMonthlyLimit(r.getMonthlyLimit());
            rule.setReceiptRequiredAbove(r.getReceiptRequiredAbove());
            rule.setUpdatedAt(LocalDateTime.now());
            rule.setUpdatedByName(actor.getName());
            ruleRepository.save(rule);

            PolicyChange change = new PolicyChange();
            change.setSummary(rule.getCategory() + ": " + String.join("; ", changes));
            change.setChangedByName(actor.getName());
            change.setChangedByEmail(actor.getEmail());
            changeRepository.save(change);
        }

        return getAll();
    }

    // Checks one claim against the policy. Called before the claim is saved.
    public PolicyCheck evaluate(User user, String category, BigDecimal amountInBase,
                                LocalDate expenseDate) {
        String name = category == null ? "" : category.trim();
        Optional<PolicyRule> found = ruleRepository.findByCategoryIgnoreCase(name);
        if (found.isEmpty()) {
            return new PolicyCheck(List.of(), false);
        }

        PolicyRule rule = found.get();
        List<String> flags = new ArrayList<>();

        if (rule.getMaxPerClaim() != null && amountInBase.compareTo(rule.getMaxPerClaim()) > 0) {
            flags.add("Over the " + rule.getCategory() + " limit of " + CURRENCY + " "
                    + plain(rule.getMaxPerClaim()) + " per claim (this claim is "
                    + CURRENCY + " " + plain(amountInBase) + ")");
        }

        if (rule.getMonthlyLimit() != null && expenseDate != null) {
            YearMonth month = YearMonth.from(expenseDate);
            BigDecimal already = expenseRepository.sumInBase(
                    user.getId(), rule.getCategory(),
                    month.atDay(1), month.atEndOfMonth(), ExpenseStatus.REJECTED);
            BigDecimal total = (already == null ? BigDecimal.ZERO : already).add(amountInBase);

            if (total.compareTo(rule.getMonthlyLimit()) > 0) {
                String monthName = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                        + " " + month.getYear();
                flags.add("Over the monthly " + rule.getCategory() + " limit of " + CURRENCY + " "
                        + plain(rule.getMonthlyLimit()) + " (" + CURRENCY + " " + plain(total)
                        + " in " + monthName + ", including this claim)");
            }
        }

        boolean receiptRequired = rule.getReceiptRequiredAbove() != null
                && amountInBase.compareTo(rule.getReceiptRequiredAbove()) > 0;

        return new PolicyCheck(flags, receiptRequired);
    }

    private void describe(List<String> out, String label, BigDecimal oldValue, BigDecimal newValue) {
        if (!same(oldValue, newValue)) {
            out.add(label + " " + money(oldValue) + " -> " + money(newValue));
        }
    }

    private boolean same(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.compareTo(b) == 0;
    }

    private String money(BigDecimal value) {
        return value == null ? "none" : CURRENCY + " " + plain(value);
    }

    private String plain(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private PolicyRuleResponse toResponse(PolicyRule r) {
        return new PolicyRuleResponse(
                r.getId(),
                r.getCategory(),
                r.getMaxPerClaim(),
                r.getMonthlyLimit(),
                r.getReceiptRequiredAbove(),
                r.getUpdatedAt(),
                r.getUpdatedByName()
        );
    }
}