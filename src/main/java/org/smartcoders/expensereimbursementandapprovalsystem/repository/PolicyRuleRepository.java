package org.smartcoders.expensereimbursementandapprovalsystem.repository;

import org.smartcoders.expensereimbursementandapprovalsystem.entity.PolicyRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyRuleRepository extends JpaRepository<PolicyRule, Long> {

    Optional<PolicyRule> findByCategoryIgnoreCase(String category);

    List<PolicyRule> findAllByOrderByIdAsc();
}