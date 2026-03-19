package com.exe.skillverse_backend.course_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "assignment_criteria")
public class AssignmentCriteria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_points", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxPoints;

    /**
     * Minimum score required to pass this criterion (Coursera pattern).
     * Defaults to 0 — mentor should set an appropriate passing threshold when creating criteria.
     */
    @Builder.Default
    @Column(name = "passing_points", nullable = false, precision = 10, scale = 2)
    @ColumnDefault("0.00")
    private BigDecimal passingPoints = BigDecimal.ZERO;

    @Column(name = "order_index")
    private Integer orderIndex;

    @Column(name = "is_required")
    @Builder.Default
    private boolean isRequired = false;
}
