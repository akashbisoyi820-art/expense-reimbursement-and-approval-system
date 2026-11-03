package org.smartcoders.expensereimbursementandapprovalsystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    // The name of the department, for example Engineering
    @Column(length = 50)
    private String department;

    // Permanent company ID built from the department code, for example ENG-0001
    @Column(unique = true, length = 20)
    private String employeeId;

    @Column(length = 80)
    private String designation;

    // A missing value (older users) means active
    private Boolean active = true;

    private LocalDate joinedOn;

    // The reporting manager
    @ManyToOne
    @JoinColumn(name = "manager_id")
    private User manager;

    public boolean isEnabled() {
        return active == null || active;
    }
}