package org.smartcoders.expensereimbursementandapprovalsystem.repository;

import org.smartcoders.expensereimbursementandapprovalsystem.entity.PolicyChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyChangeRepository extends JpaRepository<PolicyChange, Long> {

    List<PolicyChange> findTop50ByOrderByCreatedAtDescIdDesc();
}