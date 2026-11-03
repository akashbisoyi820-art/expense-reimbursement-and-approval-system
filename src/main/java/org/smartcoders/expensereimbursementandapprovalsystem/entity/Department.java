package org.smartcoders.expensereimbursementandapprovalsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Short code used in employee IDs, for example ENG. It never changes.
    @Column(nullable = false, unique = true, length = 6)
    private String code;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    @ManyToOne
    @JoinColumn(name = "head_id")
    private User head;

    @Column(nullable = false)
    private Boolean active = true;

    // The number the next employee of this department will get
    @Column(nullable = false)
    private Integer nextSeq = 1;
}