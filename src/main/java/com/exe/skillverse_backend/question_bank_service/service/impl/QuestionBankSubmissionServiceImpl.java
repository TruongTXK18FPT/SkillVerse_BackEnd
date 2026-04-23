package com.exe.skillverse_backend.question_bank_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.mentor_verification_service.entity.VerificationStatus;
import com.exe.skillverse_backend.mentor_verification_service.repository.MentorSkillVerificationRequestRepository;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionBankRequest;
import com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionRequest;
import com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionBankSubmissionRequest;
import com.exe.skillverse_backend.question_bank_service.dto.request.ReviewQuestionBankSubmissionRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankResponse;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankSubmissionResponse;
import com.exe.skillverse_backend.question_bank_service.entity.QuestionBank;
import com.exe.skillverse_backend.question_bank_service.entity.QuestionBankQuestion;
import com.exe.skillverse_backend.question_bank_service.entity.QuestionBankSubmission;
import com.exe.skillverse_backend.question_bank_service.entity.QuestionBankSubmissionQuestion;
import com.exe.skillverse_backend.question_bank_service.entity.enums.QuestionBankSubmissionStatus;
import com.exe.skillverse_backend.question_bank_service.repository.QuestionBankQuestionRepository;
import com.exe.skillverse_backend.question_bank_service.repository.QuestionBankRepository;
import com.exe.skillverse_backend.question_bank_service.repository.QuestionBankSubmissionRepository;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankService;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankSubmissionService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.shared.util.SkillNameUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class QuestionBankSubmissionServiceImpl implements QuestionBankSubmissionService {

    private static final String DEFAULT_DIFFICULTY_DISTRIBUTION =
            "{\"BEGINNER\":0.20,\"INTERMEDIATE\":0.35,\"ADVANCED\":0.30,\"EXPERT\":0.15}";

    private final QuestionBankSubmissionRepository submissionRepository;
    private final QuestionBankRepository questionBankRepository;
    private final QuestionBankQuestionRepository questionBankQuestionRepository;
    private final MentorSkillVerificationRequestRepository mentorVerificationRepository;
    private final PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;
    private final QuestionBankService questionBankService;
    private final ObjectMapper objectMapper;

    @Override
    public QuestionBankSubmissionResponse createSubmission(User mentor, CreateQuestionBankSubmissionRequest request) {
        String domain = normalizeDomain(request.getDomain());
        String industry = requireValue(request.getIndustry(), "Industry is required");
        String jobRole = requireValue(request.getJobRole(), "Job role is required");
        String skillName = normalizeSkillName(request.getSkillName());

        // [Question Bank Contribution] Mentor chỉ được gửi câu hỏi cho skill đã được xác thực.
        mentorVerificationRepository.findByMentorAndSkillAndStatusIn(
                mentor.getId(),
                skillName,
                List.of(VerificationStatus.APPROVED)
        ).orElseThrow(() -> new BadRequestException(
                "Bạn chỉ có thể đóng góp bộ câu hỏi cho skill đã được xác thực."
        ));

        validateQuestions(request.getQuestions());

        String title = normalizeOptional(request.getTitle());
        if (title == null) {
            title = buildDefaultTitle(skillName, jobRole);
        }

        QuestionBankSubmission submission = QuestionBankSubmission.builder()
                .mentor(mentor)
                .domain(domain)
                .industry(industry)
                .jobRole(jobRole)
                .skillName(skillName)
                .title(title)
                .description(normalizeOptional(request.getDescription()))
                .difficultyDistribution(normalizeOptional(request.getDifficultyDistribution()) != null
                        ? normalizeOptional(request.getDifficultyDistribution())
                        : DEFAULT_DIFFICULTY_DISTRIBUTION)
                .source(request.getSource())
                .questionCount(request.getQuestions().size())
                .build();

        List<QuestionBankSubmissionQuestion> submissionQuestions = new ArrayList<>();
        for (int index = 0; index < request.getQuestions().size(); index++) {
            CreateQuestionRequest item = request.getQuestions().get(index);
            submissionQuestions.add(QuestionBankSubmissionQuestion.builder()
                    .submission(submission)
                    .displayOrder(index + 1)
                    .questionText(requireValue(item.getQuestionText(), "Question text is required"))
                    .options(toOptionsJson(item.getOptions()))
                    .correctAnswer(normalizeCorrectAnswer(item.getCorrectAnswer()))
                    .explanation(normalizeOptional(item.getExplanation()))
                    .difficulty(normalizeDifficulty(item.getDifficulty()))
                    .skillArea(normalizeOptional(item.getSkillArea()))
                    .category(normalizeOptional(item.getCategory()))
                    .build());
        }
        submission.setQuestions(submissionQuestions);

        submission = submissionRepository.save(submission);
        log.info("Mentor {} created question bank submission {}", mentor.getId(), submission.getId());
        return toResponse(submission, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionBankSubmissionResponse> getMySubmissions(User mentor) {
        return submissionRepository.findByMentorIdOrderByCreatedAtDesc(mentor.getId())
                .stream()
                .map(submission -> toResponse(submission, false))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionBankSubmissionResponse getMySubmissionDetail(User mentor, Long submissionId) {
        QuestionBankSubmission submission = submissionRepository.findByIdAndMentorId(submissionId, mentor.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Submission not found: " + submissionId));
        return toResponse(submission, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<QuestionBankSubmissionResponse> getAdminSubmissions(List<String> statuses, Pageable pageable) {
        return submissionRepository.findByStatusInOrderByCreatedAtAsc(normalizeStatuses(statuses), pageable)
                .map(submission -> toResponse(submission, false));
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionBankSubmissionResponse getAdminSubmissionDetail(Long submissionId) {
        return toResponse(findSubmissionOrThrow(submissionId), true);
    }

    @Override
    public QuestionBankSubmissionResponse reviewSubmission(Long submissionId, User admin, ReviewQuestionBankSubmissionRequest request) {
        QuestionBankSubmission submission = findSubmissionOrThrow(submissionId);
        if (submission.getStatus() != QuestionBankSubmissionStatus.PENDING) {
            throw new BadRequestException("Submission đã được xử lý trước đó.");
        }

        SubmissionResolution resolution = resolveSubmission(submission);

        if (Boolean.TRUE.equals(request.getApproved())) {
            QuestionBank bank = resolution.bank();
            if (bank == null) {
                QuestionBankResponse createdBank = questionBankService.createBank(CreateQuestionBankRequest.builder()
                        .domain(submission.getDomain())
                        .industry(submission.getIndustry())
                        .jobRole(submission.getJobRole())
                        .skillName(submission.getSkillName())
                        .title(submission.getTitle())
                        .description(submission.getDescription())
                        .difficultyDistribution(submission.getDifficultyDistribution())
                        .build());
                bank = questionBankRepository.findById(createdBank.getId())
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question bank not found: " + createdBank.getId()));
                resolution = resolveSubmission(submission, bank);
            }

            if (!resolution.questionsToSave().isEmpty()) {
                QuestionBank finalBank = bank;
                List<QuestionBankQuestion> newQuestions = resolution.questionsToSave().stream()
                        .map(item -> QuestionBankQuestion.builder()
                                .questionBank(finalBank)
                                .questionText(item.getQuestionText())
                                .options(item.getOptions())
                                .correctAnswer(item.getCorrectAnswer())
                                .explanation(item.getExplanation())
                                .difficulty(item.getDifficulty())
                                .skillArea(item.getSkillArea())
                                .category(item.getCategory())
                                .source("MENTOR_CONTRIBUTION")
                                .isActive(true)
                                .usedCount(0)
                                .isVerified(true)
                                .verifiedBy(admin.getId())
                                .verifiedAt(LocalDateTime.now())
                                .verificationSource("MENTOR_SUBMISSION_APPROVED")
                                .build())
                        .collect(Collectors.toList());
                questionBankQuestionRepository.saveAll(newQuestions);
            }

            submission.setStatus(QuestionBankSubmissionStatus.APPROVED);
            submission.setSavedQuestionCount(resolution.savedCount());
            submission.setDuplicateQuestionCount(resolution.duplicateCount());
            submission.setResolvedQuestionBank(bank);
        } else {
            submission.setStatus(QuestionBankSubmissionStatus.REJECTED);
            submission.setSavedQuestionCount(0);
            submission.setDuplicateQuestionCount(0);
        }

        submission.setReviewNote(normalizeOptional(request.getReviewNote()));
        submission.setReviewedBy(admin);
        submission.setReviewedAt(LocalDateTime.now());

        submission = submissionRepository.save(submission);
        log.info("Admin {} reviewed question bank submission {} with status {}",
                admin.getId(), submissionId, submission.getStatus());
        return toResponse(submission, true);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingSubmissions() {
        return submissionRepository.countByStatus(QuestionBankSubmissionStatus.PENDING);
    }

    // [Question Bank Contribution] Preview và approve dùng chung luật loại trùng để admin thấy đúng số câu sẽ lưu.
    private SubmissionResolution resolveSubmission(QuestionBankSubmission submission) {
        Optional<QuestionBank> existingBank = questionBankRepository.findByExactScope(
                        submission.getDomain(),
                        submission.getIndustry(),
                        submission.getJobRole(),
                        submission.getSkillName(),
                        PageRequest.of(0, 1))
                .stream()
                .findFirst();
        return resolveSubmission(submission, existingBank.orElse(null));
    }

    // [Question Bank Contribution] Chỉ lưu những câu chưa trùng trong submission hiện tại và chưa tồn tại trong bank đích.
    private SubmissionResolution resolveSubmission(QuestionBankSubmission submission, QuestionBank targetBank) {
        Set<String> existingNormalizedQuestions = new LinkedHashSet<>();
        if (targetBank != null) {
            questionBankQuestionRepository.findActiveQuestionTextsByBankId(targetBank.getId()).stream()
                    .map(this::normalizeQuestionText)
                    .filter(text -> text != null && !text.isBlank())
                    .forEach(existingNormalizedQuestions::add);
        }

        Set<String> seenInSubmission = new LinkedHashSet<>();
        List<QuestionBankSubmissionQuestion> questionsToSave = new ArrayList<>();
        int duplicateCount = 0;

        for (QuestionBankSubmissionQuestion question : submission.getQuestions()) {
            String normalized = normalizeQuestionText(question.getQuestionText());
            if (normalized == null || normalized.isBlank()) {
                duplicateCount++;
                continue;
            }
            if (seenInSubmission.contains(normalized) || existingNormalizedQuestions.contains(normalized)) {
                duplicateCount++;
                continue;
            }

            seenInSubmission.add(normalized);
            questionsToSave.add(question);
        }

        return new SubmissionResolution(targetBank, questionsToSave, questionsToSave.size(), duplicateCount);
    }

    private QuestionBankSubmission findSubmissionOrThrow(Long submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Submission not found: " + submissionId));
    }

    private QuestionBankSubmissionResponse toResponse(QuestionBankSubmission submission, boolean includeQuestions) {
        SubmissionResolution resolution = submission.getStatus() == QuestionBankSubmissionStatus.PENDING
                ? resolveSubmission(submission)
                : new SubmissionResolution(
                submission.getResolvedQuestionBank(),
                List.of(),
                submission.getSavedQuestionCount() != null ? submission.getSavedQuestionCount() : 0,
                submission.getDuplicateQuestionCount() != null ? submission.getDuplicateQuestionCount() : 0
        );

        User mentor = submission.getMentor();
        User reviewer = submission.getReviewedBy();
        PortfolioExtendedProfile profile = portfolioExtendedProfileRepository.findByUserId(mentor.getId()).orElse(null);

        return QuestionBankSubmissionResponse.builder()
                .id(submission.getId())
                .mentorId(mentor.getId())
                .mentorName(mentor.getFullName())
                .mentorEmail(mentor.getEmail())
                .mentorAvatarUrl(mentor.getAvatarUrl())
                .mentorPortfolioSlug(profile != null ? profile.getCustomUrlSlug() : null)
                .domain(submission.getDomain())
                .industry(submission.getIndustry())
                .jobRole(submission.getJobRole())
                .skillName(submission.getSkillName())
                .title(submission.getTitle())
                .description(submission.getDescription())
                .difficultyDistribution(submission.getDifficultyDistribution())
                .status(submission.getStatus())
                .source(submission.getSource())
                .questionCount(submission.getQuestionCount())
                .savedQuestionCount(resolution.savedCount())
                .duplicateQuestionCount(resolution.duplicateCount())
                .resolvedQuestionBankId(resolution.bank() != null ? resolution.bank().getId() : null)
                .resolvedQuestionBankTitle(resolution.bank() != null ? resolution.bank().getTitle() : null)
                .reviewNote(submission.getReviewNote())
                .reviewedById(reviewer != null ? reviewer.getId() : null)
                .reviewedByName(reviewer != null ? reviewer.getFullName() : null)
                .createdAt(submission.getCreatedAt())
                .updatedAt(submission.getUpdatedAt())
                .reviewedAt(submission.getReviewedAt())
                .questions(includeQuestions
                        ? submission.getQuestions().stream().map(this::toQuestionItemResponse).collect(Collectors.toList())
                        : null)
                .build();
    }

    private QuestionBankSubmissionResponse.QuestionItemResponse toQuestionItemResponse(QuestionBankSubmissionQuestion question) {
        return QuestionBankSubmissionResponse.QuestionItemResponse.builder()
                .id(question.getId())
                .displayOrder(question.getDisplayOrder())
                .questionText(question.getQuestionText())
                .options(fromOptionsJson(question.getOptions()))
                .correctAnswer(question.getCorrectAnswer())
                .explanation(question.getExplanation())
                .difficulty(question.getDifficulty())
                .skillArea(question.getSkillArea())
                .category(question.getCategory())
                .build();
    }

    private void validateQuestions(List<CreateQuestionRequest> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new BadRequestException("Cần ít nhất 1 câu hỏi để gửi duyệt.");
        }

        for (CreateQuestionRequest question : questions) {
            requireValue(question.getQuestionText(), "Question text is required");

            if (question.getOptions() == null || question.getOptions().size() != 4) {
                throw new BadRequestException("Mỗi câu hỏi phải có đúng 4 đáp án.");
            }

            for (String option : question.getOptions()) {
                requireValue(option, "Option value is required");
            }

            normalizeCorrectAnswer(question.getCorrectAnswer());
            normalizeDifficulty(question.getDifficulty());
        }
    }

    private List<QuestionBankSubmissionStatus> normalizeStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of(
                    QuestionBankSubmissionStatus.PENDING,
                    QuestionBankSubmissionStatus.APPROVED,
                    QuestionBankSubmissionStatus.REJECTED
            );
        }

        List<QuestionBankSubmissionStatus> parsed = statuses.stream()
                .map(status -> {
                    try {
                        return QuestionBankSubmissionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
                    } catch (Exception ignored) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        return parsed.isEmpty()
                ? List.of(
                QuestionBankSubmissionStatus.PENDING,
                QuestionBankSubmissionStatus.APPROVED,
                QuestionBankSubmissionStatus.REJECTED
        )
                : parsed;
    }

    private String normalizeDomain(String domain) {
        String raw = requireValue(domain, "Domain is required");
        String upper = raw.toUpperCase(Locale.ROOT);

        if (upper.equals("IT") || upper.contains("INFORMATION TECHNOLOGY")
                || upper.contains("CÔNG NGHỆ THÔNG TIN")) {
            return "IT";
        }
        if (upper.equals("BUSINESS") || upper.contains("BUSINESS")
                || upper.contains("KINH DOANH") || upper.contains("MARKETING")
                || upper.contains("QUẢN TRỊ")) {
            return "BUSINESS";
        }
        if (upper.equals("DESIGN") || upper.contains("DESIGN")
                || upper.contains("THIẾT KẾ") || upper.contains("SÁNG TẠO")
                || upper.contains("NỘI DUNG")) {
            return "DESIGN";
        }

        throw new BadRequestException("Chỉ hỗ trợ 3 lĩnh vực chính: IT, Business, Design.");
    }

    private String normalizeSkillName(String skillName) {
        return SkillNameUtils.normalizeRequired(skillName);
    }

    private String normalizeCorrectAnswer(String correctAnswer) {
        String normalized = requireValue(correctAnswer, "Correct answer is required").toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[A-D]$")) {
            throw new BadRequestException("Đáp án đúng chỉ được là A, B, C hoặc D.");
        }
        return normalized;
    }

    private String normalizeDifficulty(String difficulty) {
        String normalized = requireValue(difficulty, "Difficulty is required").toUpperCase(Locale.ROOT);
        if (!Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT").contains(normalized)) {
            throw new BadRequestException("Độ khó không hợp lệ.");
        }
        return normalized;
    }

    private String buildDefaultTitle(String skillName, String jobRole) {
        return "Bộ câu hỏi mentor đóng góp - " + skillName.replace('_', ' ') + " / " + jobRole;
    }

    private String normalizeQuestionText(String questionText) {
        if (questionText == null) {
            return null;
        }
        return questionText.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll("[.,;:'\"!?()\\[\\]{}]", "")
                .trim();
    }

    private String requireValue(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new BadRequestException(message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String toOptionsJson(List<String> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            throw new BadRequestException("Không thể xử lý danh sách đáp án.");
        }
    }

    private List<String> fromOptionsJson(String optionsJson) {
        try {
            return objectMapper.readValue(optionsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private record SubmissionResolution(
            QuestionBank bank,
            List<QuestionBankSubmissionQuestion> questionsToSave,
            int savedCount,
            int duplicateCount
    ) {
    }
}
