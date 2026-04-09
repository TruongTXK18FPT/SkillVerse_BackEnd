package com.exe.skillverse_backend.business_service.dto.request;

import com.exe.skillverse_backend.business_service.enums.ContractType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateContractRequest {

    // ==================== IDENTIFICATION ====================
    private Long applicationId;

    @NotNull(message = "Loại hợp đồng là bắt buộc")
    private ContractType contractType;

    // ==================== JOB CONTENT ====================
    @Size(max = 300, message = "Tên chức danh không được quá 300 ký tự")
    private String jobTitle;

    @Size(max = 500, message = "Địa điểm làm việc không được quá 500 ký tự")
    private String workingLocation;

    @Size(max = 200, message = "Vị trí không được quá 200 ký tự")
    private String candidatePosition;

    private String jobDescription; // TEXT

    // ==================== PROBATION ====================
    @Min(value = 1, message = "Thời gian thử việc tối thiểu 1 tháng")
    @Max(value = 3, message = "Thời gian thử việc tối đa 3 tháng")
    private Integer probationMonths;

    @DecimalMin(value = "0", message = "Lương thử việc không được âm")
    private BigDecimal probationSalary;

    private String probationSalaryText;

    private String probationEvaluationCriteria; // TEXT

    private String probationObjectives; // TEXT

    // ==================== COMPENSATION ====================
    @NotNull(message = "Lương là bắt buộc")
    @DecimalMin(value = "0", message = "Lương không được âm")
    private BigDecimal salary;

    @NotBlank(message = "Lương bằng chữ là bắt buộc")
    private String salaryText;

    @Min(value = 1, message = "Ngày thanh toán lương phải từ 1-28")
    @Max(value = 28, message = "Ngày thanh toán lương phải từ 1-28")
    private Integer salaryPaymentDate;

    @Size(max = 100, message = "Phương thức thanh toán không được quá 100 ký tự")
    private String paymentMethod;

    @DecimalMin(value = "0", message = "Phụ cấp ăn không được âm")
    private BigDecimal mealAllowance;

    @DecimalMin(value = "0", message = "Phụ cấp đi lại không được âm")
    private BigDecimal transportAllowance;

    @DecimalMin(value = "0", message = "Phụ cấp nhà ở không được âm")
    private BigDecimal housingAllowance;

    private String otherAllowances; // TEXT / JSON

    private String bonusPolicy; // TEXT, e.g. "Lương tháng 13, thưởng hiệu suất"

    // ==================== WORKING HOURS & LEAVE ====================
    @Min(value = 1, message = "Giờ làm việc/tối thiểu 1")
    @Max(value = 12, message = "Giờ làm việc/ngày tối đa 12")
    private Integer workingHoursPerDay;

    @Min(value = 1, message = "Giờ làm việc/tối thiểu 1")
    @Max(value = 60, message = "Giờ làm việc/tuần tối đa 60")
    private Integer workingHoursPerWeek;

    @Size(max = 300, message = "Ca làm việc không được quá 300 ký tự")
    private String workingSchedule; // e.g. "Thứ 2 - Thứ 6, 08:00 - 17:00"

    private String remoteWorkPolicy; // TEXT

    @Min(value = 0, message = "Số ngày nghỉ phép không được âm")
    @Max(value = 30, message = "Số ngày nghỉ phép không được quá 30")
    private Integer annualLeaveDays;

    private String leavePolicy; // TEXT

    // ==================== BENEFITS & INSURANCE ====================
    private String insurancePolicy; // TEXT

    private Boolean healthCheckupAnnual;

    private String trainingPolicy; // TEXT

    private String otherBenefits; // TEXT

    // ==================== LEGAL CLAUSES ====================
    private String legalText; // TEXT - Custom additional terms

    private String confidentialityClause; // TEXT

    private String ipClause; // TEXT

    private String nonCompeteClause; // TEXT

    @Min(value = 0, message = "Thời gian cạnh tranh không được âm")
    @Max(value = 24, message = "Thời gian cạnh tranh không được quá 24 tháng")
    private Integer nonCompeteDurationMonths;

    @Min(value = 1, message = "Thời hạn báo trước tối thiểu 1 ngày")
    @Max(value = 90, message = "Thời hạn báo trước tối đa 90 ngày")
    private Integer terminationNoticeDays;

    private String terminationClause; // TEXT

    // ==================== DATES ====================
    @NotNull(message = "Ngày bắt đầu là bắt buộc")
    private LocalDate startDate;

    private LocalDate endDate; // Nullable for FULL_TIME indefinite

    // ==================== CANDIDATE INFO (for contract) ====================
    private String candidateAddress;

    private LocalDate candidateDateOfBirth;

    @Size(max = 50, message = "Số CMND/CCCD không được quá 50 ký tự")
    private String candidateIdCardNumber;

    @Size(max = 200, message = "Nơi cấp CMND/CCCD không được quá 200 ký tự")
    private String candidateIdCardPlace;
}
