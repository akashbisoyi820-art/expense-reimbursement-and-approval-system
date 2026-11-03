package org.smartcoders.expensereimbursementandapprovalsystem.service;

import lombok.RequiredArgsConstructor;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.CreateUserRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.UserResponse;
import org.smartcoders.expensereimbursementandapprovalsystem.dto.UserUpdateRequest;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Department;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.Role;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.User;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.DepartmentRepository;
import org.smartcoders.expensereimbursementandapprovalsystem.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PeopleService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentService departmentService;
    private final PasswordEncoder passwordEncoder;

    // ---------- Reading ----------

    public List<UserResponse> list(String search, String department, Role role, Boolean active) {
        String q = search == null ? "" : search.trim().toLowerCase();

        return userRepository.findAllByOrderByIdAsc().stream()
                .filter(u -> q.isEmpty()
                        || contains(u.getName(), q)
                        || contains(u.getEmail(), q)
                        || contains(u.getEmployeeId(), q)
                        || contains(u.getDesignation(), q))
                .filter(u -> department == null || department.isBlank()
                        || department.trim().equalsIgnoreCase(u.getDepartment()))
                .filter(u -> role == null || u.getRole() == role)
                .filter(u -> active == null || u.isEnabled() == active)
                .map(this::toResponse)
                .toList();
    }

    public UserResponse get(Long id) {
        return toResponse(find(id));
    }

    // The company directory: everyone who is still active
    public List<UserResponse> directory() {
        return userRepository.findAllByOrderByIdAsc().stream()
                .filter(User::isEnabled)
                .sorted(Comparator.comparing((User u) -> u.getName() == null ? "" : u.getName().toLowerCase()))
                .map(this::toResponse)
                .toList();
    }

    // ---------- Changing ----------

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already exists");
        }

        String departmentName = departmentService.requireActiveName(request.getDepartment());
        User manager = request.getReportingManagerId() == null
                ? null
                : requireManager(request.getReportingManagerId());

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(request.getEmail().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setDepartment(departmentName);
        user.setDesignation(blankToNull(request.getDesignation()) != null
                ? request.getDesignation().trim()
                : defaultDesignation(request.getRole()));
        user.setEmployeeId(departmentService.nextEmployeeId(departmentName));
        user.setActive(true);
        user.setJoinedOn(LocalDate.now());
        user.setManager(manager);

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request, String actorEmail) {
        User target = find(id);
        User actor = userRepository.findByEmail(actorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Role newRole = request.getRole();
        boolean newActive = Boolean.TRUE.equals(request.getActive());

        // Nobody can lock themselves out
        if (target.getId().equals(actor.getId()) && (newRole != target.getRole() || !newActive)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You cannot change your own role or deactivate your own account");
        }

        // The company must always keep at least one active admin
        boolean losingAdmin = target.getRole() == Role.ADMIN && target.isEnabled()
                && (newRole != Role.ADMIN || !newActive);
        if (losingAdmin && activeAdminCount() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one active admin is required");
        }

        // A department that has been deactivated is kept as it is, unless the person is moved
        String current = target.getDepartment();
        String requested = blankToNull(request.getDepartment());
        String departmentName = (requested == null && current != null) || (requested != null && requested.equalsIgnoreCase(current))
                ? current
                : departmentService.requireActiveName(requested);

        User manager = null;
        if (request.getReportingManagerId() != null) {
            manager = requireManager(request.getReportingManagerId());
            if (manager.getId().equals(target.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A person cannot report to themselves");
            }
            if (reportsTo(manager, target)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "That would create a reporting loop");
            }
        }

        // Someone who can no longer lead people is removed from the places where they led
        if (newRole == Role.EMPLOYEE || !newActive) {
            for (User report : userRepository.findByManagerId(target.getId())) {
                report.setManager(null);
                userRepository.save(report);
            }
            for (Department department : departmentRepository.findByHeadId(target.getId())) {
                department.setHead(null);
                departmentRepository.save(department);
            }
        }

        target.setName(request.getName().trim());
        target.setRole(newRole);
        target.setDepartment(departmentName);
        target.setDesignation(blankToNull(request.getDesignation()) == null
                ? defaultDesignation(newRole)
                : request.getDesignation().trim());
        target.setManager(manager);
        target.setActive(newActive);

        return toResponse(userRepository.save(target));
    }

    @Transactional
    public void resetPassword(Long id, String password) {
        User user = find(id);
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
    }

    // ---------- Helpers ----------

    private User find(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    // A reporting manager must be an active manager or admin
    private User requireManager(Long id) {
        User manager = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The chosen reporting manager does not exist"));
        if (!manager.isEnabled() || manager.getRole() == Role.EMPLOYEE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A reporting manager must be an active manager or admin");
        }
        return manager;
    }

    // True when 'person' reports to 'boss', directly or through other managers
    private boolean reportsTo(User person, User boss) {
        User current = person;
        int guard = 0;
        while (current != null && guard++ < 50) {
            if (current.getId().equals(boss.getId())) {
                return true;
            }
            current = current.getManager();
        }
        return false;
    }

    private long activeAdminCount() {
        return userRepository.findAllByOrderByIdAsc().stream()
                .filter(u -> u.getRole() == Role.ADMIN && u.isEnabled())
                .count();
    }

    private String defaultDesignation(Role role) {
        return switch (role) {
            case EMPLOYEE -> "Employee";
            case MANAGER -> "Manager";
            case ADMIN -> "Administrator";
        };
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private UserResponse toResponse(User u) {
        User manager = u.getManager();
        return new UserResponse(
                u.getId(),
                u.getEmployeeId(),
                u.getName(),
                u.getEmail(),
                u.getRole(),
                u.getDepartment(),
                u.getDesignation(),
                u.isEnabled(),
                manager == null ? null : manager.getId(),
                manager == null ? null : manager.getName(),
                u.getJoinedOn()
        );
    }
}