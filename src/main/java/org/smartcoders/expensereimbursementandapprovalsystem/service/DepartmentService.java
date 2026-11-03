package org.smartcoders.expensereimbursementandapprovalsystem.service;

import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.DepartmentRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.DepartmentResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Department;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Role;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.User;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.DepartmentRepository;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    // Used when a person has no valid department. It can never be deactivated.
    private static final String FALLBACK = "Other";

    private static final String[][] DEFAULTS = {
            {"ENG", "Engineering", "Builds and maintains the products"},
            {"FIN", "Finance", "Accounts, payments and budgets"},
            {"HR", "HR", "People, hiring and policies"},
            {"SAL", "Sales", "Customers and revenue"},
            {"MKT", "Marketing", "Brand and campaigns"},
            {"OPS", "Operations", "Day-to-day running of the company"},
            {"OTH", FALLBACK, "Everyone who does not belong to another department"}
    };

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    // On start: create the default departments the first time, and give every existing
    // user a valid department, an employee ID and an active flag
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (departmentRepository.count() == 0) {
            for (String[] d : DEFAULTS) {
                Department department = new Department();
                department.setCode(d[0]);
                department.setName(d[1]);
                department.setDescription(d[2]);
                departmentRepository.save(department);
            }
        }

        for (User user : userRepository.findAllByOrderByIdAsc()) {
            boolean changed = false;

            if (user.getActive() == null) {
                user.setActive(true);
                changed = true;
            }

            String name = user.getDepartment();
            if (name == null || name.isBlank() || departmentRepository.findByNameIgnoreCase(name.trim()).isEmpty()) {
                user.setDepartment(FALLBACK);
                changed = true;
            }

            if (user.getEmployeeId() == null) {
                user.setEmployeeId(nextEmployeeId(user.getDepartment()));
                changed = true;
            }

            if (changed) {
                userRepository.save(user);
            }
        }
    }

    public List<DepartmentResponse> getAll() {
        Map<String, Long> counts = activeCounts();
        return departmentRepository.findAllByOrderByNameAsc().stream()
                .map(d -> toResponse(d, counts))
                .toList();
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        String code = request.getCode() == null ? "" : request.getCode().trim().toUpperCase(Locale.ROOT);
        String name = request.getName() == null ? "" : request.getName().trim();

        if (code.isEmpty() || name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A department needs a code and a name");
        }
        if (departmentRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The code '" + code + "' is already used by another department");
        }
        if (departmentRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A department named '" + name + "' already exists");
        }

        Department department = new Department();
        department.setCode(code);
        department.setName(name);
        department.setDescription(clean(request.getDescription()));
        department.setHead(request.getHeadId() == null ? null : requireHead(request.getHeadId()));

        return toResponse(departmentRepository.save(department), activeCounts());
    }

    // The code and the name never change. The description and the head are replaced
    // by the request (an empty head clears it). A missing "active" means no change.
    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));

        Map<String, Long> counts = activeCounts();

        if (Boolean.FALSE.equals(request.getActive()) && Boolean.TRUE.equals(department.getActive())) {
            if (FALLBACK.equalsIgnoreCase(department.getName())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The '" + FALLBACK + "' department cannot be deactivated");
            }
            long people = counts.getOrDefault(department.getName().toLowerCase(Locale.ROOT), 0L);
            if (people > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Move or deactivate its " + people + " active "
                                + (people == 1 ? "employee" : "employees") + " first");
            }
        }
        if (request.getActive() != null) {
            department.setActive(request.getActive());
        }

        department.setDescription(clean(request.getDescription()));
        department.setHead(request.getHeadId() == null ? null : requireHead(request.getHeadId()));

        return toResponse(departmentRepository.save(department), counts);
    }

    // Returns the stored name of an active department. A blank name means Other.
    public String requireActiveName(String name) {
        String wanted = (name == null || name.isBlank()) ? FALLBACK : name.trim();

        Department department = departmentRepository.findByNameIgnoreCase(wanted)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown department '" + wanted + "'"));

        if (!Boolean.TRUE.equals(department.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The department '" + department.getName() + "' is not active");
        }
        return department.getName();
    }

    // Builds the next ID of a department, such as ENG-0007. Runs inside the caller's transaction.
    @Transactional
    public String nextEmployeeId(String departmentName) {
        Department department = departmentRepository.findByNameForUpdate(departmentName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown department '" + departmentName + "'"));

        int number = department.getNextSeq();
        department.setNextSeq(number + 1);
        departmentRepository.save(department);

        return department.getCode() + "-" + String.format("%04d", number);
    }

    private User requireHead(Long id) {
        User head = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The chosen department head does not exist"));
        if (!head.isEnabled() || head.getRole() == Role.EMPLOYEE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A department head must be an active manager or admin");
        }
        return head;
    }

    // Active employees per department (the keys are lower case)
    private Map<String, Long> activeCounts() {
        Map<String, Long> counts = new HashMap<>();
        for (User user : userRepository.findAllByOrderByIdAsc()) {
            if (user.isEnabled() && user.getDepartment() != null) {
                counts.merge(user.getDepartment().toLowerCase(Locale.ROOT), 1L, Long::sum);
            }
        }
        return counts;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private DepartmentResponse toResponse(Department d, Map<String, Long> counts) {
        User head = d.getHead();
        return new DepartmentResponse(
                d.getId(),
                d.getCode(),
                d.getName(),
                d.getDescription(),
                Boolean.TRUE.equals(d.getActive()),
                head == null ? null : head.getId(),
                head == null ? null : head.getName(),
                counts.getOrDefault(d.getName().toLowerCase(Locale.ROOT), 0L)
        );
    }
}