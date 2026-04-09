package com.exe.skillverse_backend.business_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateContractRequest {

    // ==================== JOB CONTENT ====================
    @Size(max = 300)
    private String jobTitle;

    @Size(max = 500)
    private String workingLocation;

    @Size(max = 200)
    private String candidatePosition;

    private String jobDescription;

    // ==================== PROBATION ====================
    @Min(1) @Max(3)
    private Integer probationMonths;

    @DecimalMin("0")
    private BigDecimal probationSalary;

    private String probationSalaryText;

    private String probationEvaluationCriteria;

    private String probationObjectives;

    // ==================== COMPENSATION ====================
    @DecimalMin("0")
    private BigDecimal salary;

    private String salaryText;

    @Min(1) @Max(28)
    private Integer salaryPaymentDate;

    @Size(max = 100)
    private String paymentMethod;

    @DecimalMin("0")
    private BigDecimal mealAllowance;

    @DecimalMin("0")
    private BigDecimal transportAllowance;

    @DecimalMin("0")
    private BigDecimal housingAllowance;

    private String otherAllowances;

    private String bonusPolicy;

    // ==================== WORKING HOURS & LEAVE ====================
    @Min(1) @Max(12)
    private Integer workingHoursPerDay;

    @Min(1) @Max(60)
    private Integer workingHoursPerWeek;

    @Size(max = 300)
    private String workingSchedule;

    private String remoteWorkPolicy;

    @Min(0) @Max(30)
    private Integer annualLeaveDays;

    private String leavePolicy;

    // ==================== BENEFITS & INSURANCE ====================
    private String insurancePolicy;

    private Boolean healthCheckupAnnual;

    private String trainingPolicy;

    private String otherBenefits;

    // ==================== LEGAL CLAUSES ====================
    private String legalText;

    private String confidentialityClause;

    private String ipClause;

    private String nonCompeteClause;

    @Min(0) @Max(24)
    private Integer nonCompeteDurationMonths;

    @Min(1) @Max(90)
    private Integer terminationNoticeDays;

    private String terminationClause;

    // ==================== DATES ====================
    private LocalDate startDate;

    private LocalDate endDate;

    // ==================== CANDIDATE INFO ====================
    private LocalDate candidateDateOfBirth;

    @Size(max = 50)
    private String candidateIdCardNumber;

    @Size(max = 200)
    private String candidateIdCardPlace;
}
