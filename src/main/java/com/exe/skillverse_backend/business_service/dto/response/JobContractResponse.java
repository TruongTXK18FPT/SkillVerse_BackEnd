package com.exe.skillverse_backend.business_service.dto.response;

import com.exe.skillverse_backend.business_service.enums.ContractStatus;
import com.exe.skillverse_backend.business_service.enums.ContractType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JobContractResponse {

    // ==================== CONTRACT INFO ====================
    private Long id;
    private Long applicationId;
    private ContractStatus status;
    private ContractType contractType;
    private String contractNumber;

    // ==================== JOB CONTENT ====================
    private String jobTitle;
    private String workingLocation;
    private String candidatePosition;
    private String jobDescription;

    // ==================== PROBATION ====================
    private Integer probationMonths;
    private BigDecimal probationSalary;
    private String probationSalaryText;
    private String probationEvaluationCriteria;
    private String probationObjectives;

    // ==================== COMPENSATION ====================
    private BigDecimal salary;
    private String salaryText;
    private Integer salaryPaymentDate;
    private String paymentMethod;
    private BigDecimal mealAllowance;
    private BigDecimal transportAllowance;
    private BigDecimal housingAllowance;
    private String otherAllowances;
    private String bonusPolicy;

    // ==================== WORKING HOURS & LEAVE ====================
    private Integer workingHoursPerDay;
    private Integer workingHoursPerWeek;
    private String workingSchedule;
    private String remoteWorkPolicy;
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
    private Integer nonCompeteDurationMonths;
    private Integer terminationNoticeDays;
    private String terminationClause;

    // ==================== DATES ====================
    private LocalDate startDate;
    private LocalDate endDate;

    // ==================== EMPLOYER INFO ====================
    private Long employerId;
    private String employerName;
    private String employerCompanyName;
    private String employerAddress;
    private String employerTaxId;
    private String employerEmail;

    // ==================== CANDIDATE INFO ====================
    private Long candidateId;
    private String candidateName;
    private String candidateEmail;
    private String candidatePhone;
    private String candidateAddress;
    private LocalDate candidateDateOfBirth;
    private String candidateIdCardNumber;
    private String candidateIdCardPlace;

    // ==================== SIGNATURES ====================
    private ContractSignatureResponse employerSignature;
    private ContractSignatureResponse candidateSignature;

    // ==================== PDF ====================
    private String signedPdfUrl;
    private LocalDateTime signedAt;

    // ==================== APPLICATION SNAPSHOT ====================
    private Long jobId;
    private String applicationJobTitle;
    private Long userId;
    private String userFullName;

    // ==================== AUDIT ====================
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
