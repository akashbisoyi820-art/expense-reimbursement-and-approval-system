package org.smartcoders.expensereimbursementandapprovalsystem.repository;

import jakarta.persistence.LockModeType;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByNameIgnoreCase(String name);

    Optional<Department> findByCodeIgnoreCase(String code);

    List<Department> findAllByOrderByNameAsc();

    List<Department> findByHeadId(Long headId);

    // Locks the row, so two people created at the same moment never get the same number
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Department d where lower(d.name) = lower(:name)")
    Optional<Department> findByNameForUpdate(@Param("name") String name);
}