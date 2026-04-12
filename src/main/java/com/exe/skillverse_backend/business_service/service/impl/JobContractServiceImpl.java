package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.CreateContractRequest;
import com.exe.skillverse_backend.business_service.dto.request.SignContractRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateContractRequest;
import com.exe.skillverse_backend.business_service.dto.response.ContractSignatureResponse;
import com.exe.skillverse_backend.business_service.dto.response.JobContractResponse;
import com.exe.skillverse_backend.business_service.entity.ContractSignature;
import com.exe.skillverse_backend.business_service.entity.JobApplication;
import com.exe.skillverse_backend.business_service.entity.JobContract;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.enums.JobApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.enums.ContractStatus;
import com.exe.skillverse_backend.business_service.enums.SignatureStatus;
import com.exe.skillverse_backend.business_service.repository.JobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.JobContractRepository;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.business_service.service.JobContractService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobContractServiceImpl implements JobContractService {

    private final JobContractRepository contractRepository;
    private final JobApplicationRepository applicationRepository;
    private final JobPostingRepository jobPostingRepository;
    private final UserRepository userRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final NotificationService notificationService;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String ROLE_EMPLOYER = "EMPLOYER";
    private static final String ROLE_CANDIDATE = "CANDIDATE";
    private static final String JOB_FILLED_REJECTION_REASON =
            "Vị trí đã được tuyển đủ sau khi hợp đồng với ứng viên khác được ký thành công.";

    @Override
    @Transactional
    public JobContractResponse createContract(CreateContractRequest request, Long userId) {
        JobApplication application = applicationRepository.findById(request.getApplicationId())
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        if (application.getStatus() != JobApplicationStatus.ACCEPTED
                && application.getStatus() != JobApplicationStatus.INTERVIEWED) {
            throw new IllegalStateException(
                    "Can only create contract for ACCEPTED or INTERVIEWED application. Current: " + application.getStatus());
        }

        if (application.getJobPosting() == null ||
            application.getJobPosting().getRecruiterProfile() == null ||
            application.getJobPosting().getRecruiterProfile().getUser() == null) {
            throw new IllegalArgumentException("Recruiter profile not found");
        }

        Long recruiterUserId = application.getJobPosting().getRecruiterProfile().getUser().getId();
        if (!recruiterUserId.equals(userId)) {
            throw new IllegalStateException("Only the recruiter who posted this job can create a contract");
        }

        if (contractRepository.existsByApplicationId(request.getApplicationId())) {
            throw new IllegalStateException("Contract already exists for this application");
        }

        // Fetch candidate info
        User candidate = userRepository.findById(application.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));

        // Fetch employer (recruiter) info
        RecruiterProfile recruiterProfile = application.getJobPosting().getRecruiterProfile();
        User employer = userRepository.findById(recruiterUserId)
                .orElseThrow(() -> new IllegalArgumentException("Employer not found"));

        // Build signature placeholders
        ContractSignature employerSignature = ContractSignature.builder()
                .signedBy(employer.getId())
                .signedByName(getFullName(employer))
                .signedByRole(ROLE_EMPLOYER)
                .status(SignatureStatus.NOT_SIGNED)
                .build();

        ContractSignature candidateSignature = ContractSignature.builder()
                .signedBy(candidate.getId())
                .signedByName(getFullName(candidate))
                .signedByRole(ROLE_CANDIDATE)
                .status(SignatureStatus.NOT_SIGNED)
                .build();

        // Build contract with all fields
        JobContract contract = JobContract.builder()
                // Application & status
                .application(application)
                .status(ContractStatus.DRAFT)
                .contractType(request.getContractType())
                // Job content
                .jobTitle(request.getJobTitle() != null ? request.getJobTitle()
                        : application.getJobPosting().getTitle())
                .workingLocation(request.getWorkingLocation())
                .candidatePosition(request.getCandidatePosition())
                .jobDescription(request.getJobDescription())
                // Compensation
                .salary(request.getSalary())
                .salaryText(request.getSalaryText())
                .salaryPaymentDate(request.getSalaryPaymentDate())
                .paymentMethod(request.getPaymentMethod())
                .mealAllowance(request.getMealAllowance())
                .transportAllowance(request.getTransportAllowance())
                .housingAllowance(request.getHousingAllowance())
                .otherAllowances(request.getOtherAllowances())
                .bonusPolicy(request.getBonusPolicy())
                // Probation
                .probationMonths(request.getProbationMonths())
                .probationSalary(request.getProbationSalary())
                .probationSalaryText(request.getProbationSalaryText())
                .probationEvaluationCriteria(request.getProbationEvaluationCriteria())
                .probationObjectives(request.getProbationObjectives())
                // Working hours & leave
                .workingHoursPerDay(request.getWorkingHoursPerDay())
                .workingHoursPerWeek(request.getWorkingHoursPerWeek())
                .workingSchedule(request.getWorkingSchedule())
                .remoteWorkPolicy(request.getRemoteWorkPolicy())
                .annualLeaveDays(request.getAnnualLeaveDays())
                .leavePolicy(request.getLeavePolicy())
                // Benefits
                .insurancePolicy(request.getInsurancePolicy())
                .healthCheckupAnnual(request.getHealthCheckupAnnual())
                .trainingPolicy(request.getTrainingPolicy())
                .otherBenefits(request.getOtherBenefits())
                // Legal clauses
                .legalText(request.getLegalText())
                .confidentialityClause(request.getConfidentialityClause())
                .ipClause(request.getIpClause())
                .nonCompeteClause(request.getNonCompeteClause())
                .nonCompeteDurationMonths(request.getNonCompeteDurationMonths())
                .terminationNoticeDays(request.getTerminationNoticeDays())
                .terminationClause(request.getTerminationClause())
                // Dates
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                // Employer info (denormalized)
                .employerId(employer.getId())
                .employerName(getFullName(employer))
                .employerCompanyName(recruiterProfile.getCompanyName())
                .employerAddress(recruiterProfile.getCompanyAddress())
                .employerTaxId(recruiterProfile.getTaxCodeOrBusinessRegistrationNumber())
                .employerEmail(employer.getEmail())
                // Candidate info (denormalized)
                .candidateId(candidate.getId())
                .candidateName(getFullName(candidate))
                .candidateEmail(candidate.getEmail())
                .candidatePhone(candidate.getPhoneNumber())
                .candidateAddress(request.getCandidateAddress())
                .candidateDateOfBirth(request.getCandidateDateOfBirth())
                .candidateIdCardNumber(request.getCandidateIdCardNumber())
                .candidateIdCardPlace(request.getCandidateIdCardPlace())
                .build();

        contract.setEmployerSignature(employerSignature);
        contract.setCandidateSignature(candidateSignature);

        JobContract saved = contractRepository.save(contract);

        // Generate contract number after save (has ID)
        saved.setContractNumber(generateContractNumber(saved.getId()));
        saved = contractRepository.save(saved);

        // Notify employer: draft created
        notifyContractCreated(saved, employer.getId());

        return mapToResponse(saved);
    }

    // ======= Notify on contract creation (draft ready) =======
    private void notifyContractCreated(JobContract contract, Long employerId) {
        notificationService.createNotification(
            employerId,
            "Hợp đồng đã được tạo",
            "Hợp đồng cho vị trí '" + contract.getJobTitle() + "' đã được tạo dưới dạng bản nháp.",
            NotificationType.CONTRACT_SENT_FOR_SIGNATURE,
            contract.getId().toString()
        );
    }

    // ======= Notify on send for signature (candidate) =======
    private void notifySentForSignature(JobContract contract) {
        notificationService.createNotification(
            contract.getCandidateId(),
            "Hợp đồng chờ ký",
            "Hợp đồng '" + contract.getJobTitle() + "' đang chờ bạn ký. Vui lòng xem và ký trong 72 giờ.",
            NotificationType.CONTRACT_SENT_FOR_SIGNATURE,
            contract.getId().toString(),
            contract.getEmployerId()
        );
        // Also notify employer that contract was sent
        notificationService.createNotification(
            contract.getEmployerId(),
            "Đã gửi hợp đồng",
            "Hợp đồng '" + contract.getJobTitle() + "' đã được gửi đến ứng viên để ký.",
            NotificationType.CONTRACT_SENT_FOR_SIGNATURE,
            contract.getId().toString()
        );
    }

    // ======= Notify on partial sign (other party needs to sign) =======
    private void notifyPendingEmployerSignature(JobContract contract) {
        notificationService.createNotification(
            contract.getEmployerId(),
            "Hợp đồng chờ ký",
            "Ứng viên đã ký hợp đồng '" + contract.getJobTitle() + "'. Bạn cần đối ký để hoàn tất.",
            NotificationType.CONTRACT_SIGNED,
            contract.getId().toString(),
            contract.getCandidateId()
        );
    }

    private void notifyPendingCandidateSignature(JobContract contract) {
        notificationService.createNotification(
            contract.getCandidateId(),
            "Hợp đồng chờ ký",
            "Nhà tuyển dụng đã ký hợp đồng '" + contract.getJobTitle() + "'. Bạn cần ký để hoàn tất.",
            NotificationType.CONTRACT_SIGNED,
            contract.getId().toString(),
            contract.getEmployerId()
        );
    }

    // ======= Notify on fully signed =======
    private void notifyContractSigned(JobContract contract) {
        // Notify both parties
        notificationService.createNotification(
            contract.getEmployerId(),
            "Hợp đồng đã ký thành công",
            "Hợp đồng '" + contract.getJobTitle() + "' đã được cả hai bên ký và có hiệu lực.",
            NotificationType.CONTRACT_SIGNED,
            contract.getId().toString(),
            contract.getCandidateId()
        );
        notificationService.createNotification(
            contract.getCandidateId(),
            "Hợp đồng đã ký thành công",
            "Hợp đồng '" + contract.getJobTitle() + "' đã được cả hai bên ký và có hiệu lực.",
            NotificationType.CONTRACT_SIGNED,
            contract.getId().toString(),
            contract.getEmployerId()
        );
    }

    // ======= Notify on rejection =======
    private void notifyContractRejected(JobContract contract, Long rejectedByUserId) {
        Long recipientId = rejectedByUserId.equals(contract.getEmployerId())
            ? contract.getCandidateId() : contract.getEmployerId();
        String rejecterLabel = rejectedByUserId.equals(contract.getEmployerId()) ? "Nhà tuyển dụng" : "Ứng viên";
        notificationService.createNotification(
            recipientId,
            "Hợp đồng bị từ chối",
            rejecterLabel + " đã từ chối ký hợp đồng '" + contract.getJobTitle() + "'.",
            NotificationType.CONTRACT_REJECTED,
            contract.getId().toString(),
            rejectedByUserId
        );
    }

    // ======= Notify on cancellation =======
    private void notifyContractCancelled(JobContract contract, Long cancelledByUserId) {
        Long recipientId = cancelledByUserId.equals(contract.getEmployerId())
            ? contract.getCandidateId() : contract.getEmployerId();
        String cancellerLabel = cancelledByUserId.equals(contract.getEmployerId()) ? "Nhà tuyển dụng" : "Ứng viên";
        notificationService.createNotification(
            recipientId,
            "Hợp đồng bị hủy",
            cancellerLabel + " đã hủy hợp đồng '" + contract.getJobTitle() + "'.",
            NotificationType.CONTRACT_CANCELLED,
            contract.getId().toString(),
            cancelledByUserId
        );
    }

    // ======= Notify on expiration =======
    private void notifyContractExpired(JobContract contract) {
        notificationService.createNotification(
            contract.getEmployerId(),
            "Hợp đồng đã hết hạn ký",
            "Hợp đồng '" + contract.getJobTitle() + "' đã hết hạn ký (72 giờ) và không còn hiệu lực.",
            NotificationType.CONTRACT_EXPIRED,
            contract.getId().toString()
        );
        notificationService.createNotification(
            contract.getCandidateId(),
            "Hợp đồng đã hết hạn ký",
            "Hợp đồng '" + contract.getJobTitle() + "' đã hết hạn ký (72 giờ) và không còn hiệu lực.",
            NotificationType.CONTRACT_EXPIRED,
            contract.getId().toString()
        );
    }

    @Override
    @Transactional
    public JobContractResponse sendForSignature(Long contractId, Long userId) {
        JobContract contract = findOrThrow(contractId);

        if (!contract.getEmployerId().equals(userId)) {
            throw new IllegalStateException("Only the employer can send contract for signature");
        }

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new IllegalStateException("Contract must be in DRAFT status to send for signature");
        }

        contract.setStatus(ContractStatus.PENDING_SIGNER);
        JobContract saved = contractRepository.save(contract);

        // Notify candidate: contract awaiting their signature
        notifySentForSignature(saved);

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public JobContractResponse signContract(Long contractId, SignContractRequest request, Long userId) {
        JobContract contract = findOrThrow(contractId);

        boolean isEmployer = contract.getEmployerId().equals(userId);
        boolean isCandidate = contract.getCandidateId().equals(userId);

        if (!isEmployer && !isCandidate) {
            throw new IllegalStateException("You are not a party to this contract");
        }

        ContractSignature signature;
        ContractStatus requiredStatus;
        ContractStatus nextPendingStatus;

        if (isEmployer) {
            signature = contract.getEmployerSignature();
            requiredStatus = ContractStatus.PENDING_EMPLOYER;
            nextPendingStatus = ContractStatus.PENDING_SIGNER;
        } else {
            signature = contract.getCandidateSignature();
            requiredStatus = ContractStatus.PENDING_SIGNER;
            nextPendingStatus = ContractStatus.PENDING_EMPLOYER;
        }

        if (contract.getStatus() != requiredStatus) {
            throw new IllegalStateException(
                    isEmployer
                            ? "Contract is not awaiting employer signature"
                            : "Contract is not awaiting candidate signature");
        }

        if (signature == null) {
            throw new IllegalStateException("Signature placeholder is missing for this contract");
        }

        if (signature.getStatus() == SignatureStatus.SIGNED) {
            throw new IllegalStateException("This party has already signed the contract");
        }

        if (signature.getStatus() == SignatureStatus.REJECTED) {
            throw new IllegalStateException("This party has already rejected the contract");
        }

        if ("REJECT".equalsIgnoreCase(request.getAction())) {
            signature.setStatus(SignatureStatus.REJECTED);
            signature.setSignedAt(LocalDateTime.now(VN_ZONE));
            signature.setIpAddress(request.getIpAddress());
            signature.setUserAgent(request.getUserAgent());
            contract.setStatus(ContractStatus.REJECTED);
        } else if ("SIGN".equalsIgnoreCase(request.getAction())) {
            signature.setStatus(SignatureStatus.SIGNED);
            signature.setSignedAt(LocalDateTime.now(VN_ZONE));
            signature.setSignatureImageUrl(request.getSignatureImageUrl());
            signature.setIpAddress(request.getIpAddress());
            signature.setUserAgent(request.getUserAgent());

            ContractSignature other = isEmployer ? contract.getCandidateSignature() : contract.getEmployerSignature();
            if (other.getStatus() == SignatureStatus.SIGNED) {
                contract.setStatus(ContractStatus.SIGNED);
                contract.setSignedAt(LocalDateTime.now(VN_ZONE));
            } else {
                // Move to the other party
                contract.setStatus(nextPendingStatus);
            }
        } else {
            throw new IllegalArgumentException("Invalid action. Must be SIGN or REJECT");
        }

        JobContract saved = contractRepository.saveAndFlush(contract);
        if (saved.getStatus() == ContractStatus.SIGNED) {
            finalizeJobWhenHiringTargetReached(saved);
            notifyContractSigned(saved);
        } else if (saved.getStatus() == ContractStatus.PENDING_EMPLOYER) {
            notifyPendingEmployerSignature(saved);
        } else if (saved.getStatus() == ContractStatus.PENDING_SIGNER) {
            notifyPendingCandidateSignature(saved);
        } else if (saved.getStatus() == ContractStatus.REJECTED) {
            notifyContractRejected(saved, userId);
        }
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public JobContractResponse rejectContract(Long contractId, String reason, Long userId) {
        JobContract contract = findOrThrow(contractId);

        boolean isEmployer = contract.getEmployerId().equals(userId);
        boolean isCandidate = contract.getCandidateId().equals(userId);

        if (!isEmployer && !isCandidate) {
            throw new IllegalStateException("You are not a party to this contract");
        }

        contract.setStatus(ContractStatus.REJECTED);
        JobContract saved = contractRepository.save(contract);

        // Notify the other party of rejection
        notifyContractRejected(saved, userId);

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public JobContractResponse cancelContract(Long contractId, Long userId) {
        JobContract contract = findOrThrow(contractId);

        if (!contract.getEmployerId().equals(userId)) {
            throw new IllegalStateException("Only the employer can cancel a contract");
        }

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT contracts can be cancelled");
        }

        contract.setStatus(ContractStatus.CANCELLED);
        JobContract saved = contractRepository.save(contract);

        // Notify candidate: contract was cancelled
        notifyContractCancelled(saved, userId);

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public JobContractResponse updateContract(Long contractId, UpdateContractRequest request, Long userId) {
        JobContract contract = findOrThrow(contractId);

        if (!contract.getEmployerId().equals(userId)) {
            throw new IllegalStateException("Only the employer can update a contract");
        }

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT contracts can be updated");
        }

        // Copy all non-null fields from request to contract
        copyNonNullFields(request, contract);

        JobContract saved = contractRepository.save(contract);
        return mapToResponse(saved);
    }

    /**
     * Copy all non-null fields from request DTO to entity.
     * Handles primitive wrappers (Integer, Boolean, etc.) correctly.
     */
    private void copyNonNullFields(Object request, JobContract contract) {
        Field[] fields = request.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(request);
                if (value != null) {
                    String fieldName = field.getName();
                    // Map camelCase to field name (DTO uses camelCase, entity uses camelCase)
                    try {
                        Field entityField = JobContract.class.getDeclaredField(fieldName);
                        entityField.setAccessible(true);
                        entityField.set(contract, value);
                    } catch (NoSuchFieldException e) {
                        // Field not in entity, skip
                    }
                }
            } catch (IllegalAccessException e) {
                // Skip inaccessible fields
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public JobContractResponse getContractById(Long id) {
        JobContract contract = findOrThrow(id);
        return mapToResponse(contract);
    }

    @Override
    @Transactional(readOnly = true)
    public JobContractResponse getContractByApplication(Long applicationId) {
        JobContract contract = contractRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found for this application"));
        return mapToResponse(contract);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobContractResponse> getMyContracts(String role, Long userId) {
        List<JobContract> contracts;
        if ("EMPLOYER".equalsIgnoreCase(role)) {
            contracts = contractRepository.findByEmployerIdOrderByCreatedAtDesc(userId);
        } else {
            contracts = contractRepository.findByCandidateIdOrderByCreatedAtDesc(userId);
        }
        return contracts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ==================== Helper methods ====================

    private JobContract findOrThrow(Long contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found"));
    }

    private void finalizeJobWhenHiringTargetReached(JobContract signedContract) {
        JobApplication signedApplication = signedContract.getApplication();
        if (signedApplication == null || signedApplication.getJobPosting() == null) {
            return;
        }

        JobPosting jobPosting = signedApplication.getJobPosting();
        int hiringQuantity = normalizeHiringQuantity(jobPosting.getHiringQuantity());
        long signedContracts = contractRepository.countByApplicationJobPostingIdAndStatus(
                jobPosting.getId(),
                ContractStatus.SIGNED);

        if (signedContracts < hiringQuantity) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        closeJobPosting(jobPosting, now);
        rejectRemainingApplications(jobPosting.getId(), signedApplication.getId(), now);
        cancelRemainingContracts(jobPosting.getId(), signedContract.getId());
    }

    private int normalizeHiringQuantity(Integer hiringQuantity) {
        return hiringQuantity == null || hiringQuantity < 1 ? 1 : hiringQuantity;
    }

    private void closeJobPosting(JobPosting jobPosting, LocalDateTime closedAt) {
        jobPosting.setStatus(JobStatus.CLOSED);
        if (jobPosting.getClosedAt() == null) {
            jobPosting.setClosedAt(closedAt);
        }
        jobPostingRepository.save(jobPosting);
    }

    private void rejectRemainingApplications(Long jobPostingId, Long signedApplicationId, LocalDateTime processedAt) {
        List<JobApplication> applications = applicationRepository.findByJobPostingIdOrderByAppliedAtDesc(jobPostingId);
        for (JobApplication application : applications) {
            if (application.getId().equals(signedApplicationId)
                    || application.getStatus() == JobApplicationStatus.REJECTED) {
                continue;
            }

            application.setStatus(JobApplicationStatus.REJECTED);
            application.setAcceptanceMessage(null);
            application.setRejectionReason(JOB_FILLED_REJECTION_REASON);
            application.setProcessedAt(processedAt);
            applicationRepository.save(application);
        }
    }

    private void cancelRemainingContracts(Long jobPostingId, Long signedContractId) {
        List<JobContract> contracts = contractRepository.findByApplicationJobPostingIdAndStatusIn(
                jobPostingId,
                List.of(ContractStatus.DRAFT, ContractStatus.PENDING_SIGNER, ContractStatus.PENDING_EMPLOYER));

        for (JobContract contract : contracts) {
            if (contract.getId().equals(signedContractId)) {
                continue;
            }
            contract.setStatus(ContractStatus.CANCELLED);
            contractRepository.save(contract);
        }
    }

    private String generateContractNumber(Long id) {
        int year = Year.now(VN_ZONE).getValue();
        return "HD-" + year + "-" + String.format("%05d", id);
    }

    private String getFullName(User user) {
        String fullName = user.getFullName();
        if (fullName == null || fullName.isBlank()) {
            return user.getEmail();
        }
        return fullName;
    }

    private JobContractResponse mapToResponse(JobContract contract) {
        ContractSignatureResponse employerSigResp = null;
        ContractSignatureResponse candidateSigResp = null;

        if (contract.getEmployerSignature() != null) {
            ContractSignature sig = contract.getEmployerSignature();
            employerSigResp = ContractSignatureResponse.builder()
                    .id(sig.getId())
                    .signedBy(sig.getSignedBy())
                    .signedByName(sig.getSignedByName())
                    .signedByRole(sig.getSignedByRole())
                    .status(sig.getStatus())
                    .signatureImageUrl(sig.getSignatureImageUrl())
                    .signedAt(sig.getSignedAt())
                    .build();
        }

        if (contract.getCandidateSignature() != null) {
            ContractSignature sig = contract.getCandidateSignature();
            candidateSigResp = ContractSignatureResponse.builder()
                    .id(sig.getId())
                    .signedBy(sig.getSignedBy())
                    .signedByName(sig.getSignedByName())
                    .signedByRole(sig.getSignedByRole())
                    .status(sig.getStatus())
                    .signatureImageUrl(sig.getSignatureImageUrl())
                    .signedAt(sig.getSignedAt())
                    .build();
        }

        // Application snapshot
        Long jobId = null;
        String jobTitle = null;
        Long userId = null;
        String userFullName = null;

        if (contract.getApplication() != null) {
            JobApplication app = contract.getApplication();
            if (app.getJobPosting() != null) {
                jobId = app.getJobPosting().getId();
                if (jobTitle == null) jobTitle = app.getJobPosting().getTitle();
            }
            if (app.getUser() != null) {
                userId = app.getUser().getId();
                userFullName = getFullName(app.getUser());
            }
        }

        return JobContractResponse.builder()
                // Core
                .id(contract.getId())
                .applicationId(contract.getApplication() != null ? contract.getApplication().getId() : null)
                .status(contract.getStatus())
                .contractType(contract.getContractType())
                .contractNumber(contract.getContractNumber())
                // Job content
                .jobTitle(contract.getJobTitle())
                .workingLocation(contract.getWorkingLocation())
                .candidatePosition(contract.getCandidatePosition())
                .jobDescription(contract.getJobDescription())
                // Probation
                .probationMonths(contract.getProbationMonths())
                .probationSalary(contract.getProbationSalary())
                .probationSalaryText(contract.getProbationSalaryText())
                .probationEvaluationCriteria(contract.getProbationEvaluationCriteria())
                .probationObjectives(contract.getProbationObjectives())
                // Compensation
                .salary(contract.getSalary())
                .salaryText(contract.getSalaryText())
                .salaryPaymentDate(contract.getSalaryPaymentDate())
                .paymentMethod(contract.getPaymentMethod())
                .mealAllowance(contract.getMealAllowance())
                .transportAllowance(contract.getTransportAllowance())
                .housingAllowance(contract.getHousingAllowance())
                .otherAllowances(contract.getOtherAllowances())
                .bonusPolicy(contract.getBonusPolicy())
                // Working hours & leave
                .workingHoursPerDay(contract.getWorkingHoursPerDay())
                .workingHoursPerWeek(contract.getWorkingHoursPerWeek())
                .workingSchedule(contract.getWorkingSchedule())
                .remoteWorkPolicy(contract.getRemoteWorkPolicy())
                .annualLeaveDays(contract.getAnnualLeaveDays())
                .leavePolicy(contract.getLeavePolicy())
                // Benefits
                .insurancePolicy(contract.getInsurancePolicy())
                .healthCheckupAnnual(contract.getHealthCheckupAnnual())
                .trainingPolicy(contract.getTrainingPolicy())
                .otherBenefits(contract.getOtherBenefits())
                // Legal clauses
                .legalText(contract.getLegalText())
                .confidentialityClause(contract.getConfidentialityClause())
                .ipClause(contract.getIpClause())
                .nonCompeteClause(contract.getNonCompeteClause())
                .nonCompeteDurationMonths(contract.getNonCompeteDurationMonths())
                .terminationNoticeDays(contract.getTerminationNoticeDays())
                .terminationClause(contract.getTerminationClause())
                // Dates
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                // Employer
                .employerId(contract.getEmployerId())
                .employerName(contract.getEmployerName())
                .employerCompanyName(contract.getEmployerCompanyName())
                .employerAddress(contract.getEmployerAddress())
                .employerTaxId(contract.getEmployerTaxId())
                .employerEmail(contract.getEmployerEmail())
                // Candidate
                .candidateId(contract.getCandidateId())
                .candidateName(contract.getCandidateName())
                .candidateEmail(contract.getCandidateEmail())
                .candidatePhone(contract.getCandidatePhone())
                .candidateAddress(contract.getCandidateAddress())
                .candidateDateOfBirth(contract.getCandidateDateOfBirth())
                .candidateIdCardNumber(contract.getCandidateIdCardNumber())
                .candidateIdCardPlace(contract.getCandidateIdCardPlace())
                // Signatures & PDF
                .employerSignature(employerSigResp)
                .candidateSignature(candidateSigResp)
                .signedPdfUrl(contract.getSignedPdfUrl())
                .signedAt(contract.getSignedAt())
                // Application snapshot
                .jobId(jobId)
                .applicationJobTitle(jobTitle)
                .userId(userId)
                .userFullName(userFullName)
                // Metadata
                .version(contract.getVersion())
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt())
                .build();
    }
}
