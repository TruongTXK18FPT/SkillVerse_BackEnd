package com.exe.skillverse_backend.business_service.entity;

import com.exe.skillverse_backend.business_service.enums.ContractStatus;
import com.exe.skillverse_backend.business_service.enums.ContractType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "job_contracts",
    uniqueConstraints = @UniqueConstraint(name = "uk_contract_application", columnNames = "application_id"))
public class JobContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private JobApplication application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContractStatus status = ContractStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 20)
    private ContractType contractType;

    @Column(name = "contract_number", length = 50)
    private String contractNumber;

    // =====================================================
    // JOB CONTENT & POSITION
    // =====================================================
    @Column(name = "job_title", length = 300)
    private String jobTitle;

    @Column(name = "working_location", length = 500)
    private String workingLocation;

    @Column(name = "candidate_position", length = 200)
    private String candidatePosition;

    @Column(name = "job_description", columnDefinition = "TEXT")
    private String jobDescription;

    // =====================================================
    // PROBATION (for PROBATION contract type)
    // =====================================================
    @Column(name = "probation_months")
    private Integer probationMonths;

    @Column(name = "probation_salary", precision = 15, scale = 2)
    private BigDecimal probationSalary;

    @Column(name = "probation_salary_text", length = 500)
    private String probationSalaryText;

    @Column(name = "probation_evaluation_criteria", columnDefinition = "TEXT")
    private String probationEvaluationCriteria;

    @Column(name = "probation_objectives", columnDefinition = "TEXT")
    private String probationObjectives;

    // =====================================================
    // COMPENSATION
    // =====================================================
    @Column(precision = 15, scale = 2)
    private BigDecimal salary;

    @Column(name = "salary_text", length = 500)
    private String salaryText;

    @Column(name = "salary_payment_date")
    private Integer salaryPaymentDate; // day of month, e.g. 10

    @Column(name = "payment_method", length = 100)
    private String paymentMethod; // bank_transfer, cash, etc.

    // Allowances
    @Column(name = "meal_allowance", precision = 15, scale = 2)
    private BigDecimal mealAllowance;

    @Column(name = "transport_allowance", precision = 15, scale = 2)
    private BigDecimal transportAllowance;

    @Column(name = "housing_allowance", precision = 15, scale = 2)
    private BigDecimal housingAllowance;

    @Column(name = "other_allowances", columnDefinition = "TEXT")
    private String otherAllowances; // JSON or text describing other allowances

    @Column(name = "bonus_policy", columnDefinition = "TEXT")
    private String bonusPolicy; // e.g. 13th month, performance bonus

    // =====================================================
    // WORKING HOURS & LEAVE
    // =====================================================
    @Column(name = "working_hours_per_day")
    private Integer workingHoursPerDay;

    @Column(name = "working_hours_per_week")
    private Integer workingHoursPerWeek;

    @Column(name = "working_schedule", length = 300)
    private String workingSchedule; // e.g. Mon-Fri 8:00-17:00

    @Column(name = "remote_work_policy", columnDefinition = "TEXT")
    private String remoteWorkPolicy; // allowed days, conditions

    @Column(name = "annual_leave_days")
    private Integer annualLeaveDays; // days per year

    @Column(name = "leave_policy", columnDefinition = "TEXT")
    private String leavePolicy; // sick, maternity, personal leave terms

    // =====================================================
    // BENEFITS & INSURANCE
    // =====================================================
    @Column(name = "insurance_policy", columnDefinition = "TEXT")
    private String insurancePolicy; // SI, YT, TN details

    @Column(name = "health_checkup_annual")
    private Boolean healthCheckupAnnual;

    @Column(name = "training_policy", columnDefinition = "TEXT")
    private String trainingPolicy;

    @Column(name = "other_benefits", columnDefinition = "TEXT")
    private String otherBenefits;

    // =====================================================
    // LEGAL / TERMINATION / IP
    // =====================================================
    @Column(name = "legal_text", columnDefinition = "TEXT")
    private String legalText; // Custom legal terms (JSON or full text)

    @Column(name = "confidentiality_clause", columnDefinition = "TEXT")
    private String confidentialityClause;

    @Column(name = "ip_clause", columnDefinition = "TEXT")
    private String ipClause;

    @Column(name = "non_compete_clause", columnDefinition = "TEXT")
    private String nonCompeteClause;

    @Column(name = "non_compete_duration_months")
    private Integer nonCompeteDurationMonths;

    @Column(name = "termination_notice_days")
    private Integer terminationNoticeDays; // days notice required

    @Column(name = "termination_clause", columnDefinition = "TEXT")
    private String terminationClause;

    // =====================================================
    // PARTIES (denormalized)
    // =====================================================
    @Column(name = "employer_id", nullable = false)
    private Long employerId;

    @Column(name = "employer_name", length = 200)
    private String employerName;

    @Column(name = "employer_company_name", length = 300)
    private String employerCompanyName;

    @Column(name = "employer_address", length = 500)
    private String employerAddress;

    @Column(name = "employer_tax_id", length = 50)
    private String employerTaxId;

    @Column(name = "employer_email", length = 200)
    private String employerEmail;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "candidate_name", length = 200)
    private String candidateName;

    @Column(name = "candidate_email", length = 200)
    private String candidateEmail;

    @Column(name = "candidate_phone", length = 30)
    private String candidatePhone;

    @Column(name = "candidate_address", length = 500)
    private String candidateAddress;

    @Column(name = "candidate_date_of_birth")
    private LocalDate candidateDateOfBirth;

    @Column(name = "candidate_id_card_number", length = 50)
    private String candidateIdCardNumber;

    @Column(name = "candidate_id_card_place", length = 200)
    private String candidateIdCardPlace;

    // =====================================================
    // DATES
    // =====================================================
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    // =====================================================
    // SIGNATURES
    // =====================================================
    @Builder.Default
    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ContractSignature> signatures = new ArrayList<>();

    @Column(name = "signed_pdf_url", length = 500)
    private String signedPdfUrl;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    // =====================================================
    // METADATA
    // =====================================================
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Transient
    public ContractSignature getEmployerSignature() {
        return findSignatureByRole("EMPLOYER");
    }

    @Transient
    public ContractSignature getCandidateSignature() {
        return findSignatureByRole("CANDIDATE");
    }

    public void setEmployerSignature(ContractSignature signature) {
        replaceSignature("EMPLOYER", signature);
    }

    public void setCandidateSignature(ContractSignature signature) {
        replaceSignature("CANDIDATE", signature);
    }

    private ContractSignature findSignatureByRole(String role) {
        if (signatures == null) {
            return null;
        }
        return signatures.stream()
                .filter(signature -> role.equalsIgnoreCase(signature.getSignedByRole()))
                .findFirst()
                .orElse(null);
    }

    private void replaceSignature(String role, ContractSignature signature) {
        if (signatures == null) {
            signatures = new ArrayList<>();
        }
        signatures.removeIf(existing -> role.equalsIgnoreCase(existing.getSignedByRole()));
        if (signature != null) {
            signature.setSignedByRole(role);
            signature.setContract(this);
            signatures.add(signature);
        }
    }
}
