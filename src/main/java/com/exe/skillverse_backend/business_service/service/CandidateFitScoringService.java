package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.dto.request.CandidateSearchRequest;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.portfolio_service.dto.CandidateFitAnalysisDTO;
import com.exe.skillverse_backend.portfolio_service.dto.CompletedMissionDTO;
import com.exe.skillverse_backend.portfolio_service.entity.GeneratedCV;
import com.exe.skillverse_backend.portfolio_service.entity.ExternalCertificate;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioProject;
import com.exe.skillverse_backend.portfolio_service.entity.UserVerifiedSkill;
import com.exe.skillverse_backend.portfolio_service.repository.ExternalCertificateRepository;
import com.exe.skillverse_backend.portfolio_service.repository.GeneratedCVRepository;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioProjectRepository;
import com.exe.skillverse_backend.portfolio_service.repository.UserVerifiedSkillRepository;
import com.exe.skillverse_backend.portfolio_service.service.PortfolioService;
import com.exe.skillverse_backend.student_skill_verification.entity.StudentSkillVerificationRequest;
import com.exe.skillverse_backend.student_skill_verification.repository.StudentSkillVerificationRequestRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deterministic candidate fit scoring for recruiter search.
 *
 * The output keeps the old 0-1 score contract while adding recruiter-facing
 * explanations, evidence, risk flags, and interview actions.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CandidateFitScoringService {

    private static final double SKILL_WEIGHT = 0.35;
    private static final double EXPERIENCE_WEIGHT = 0.15;
    private static final double EVIDENCE_WEIGHT = 0.20;
    private static final double DELIVERY_WEIGHT = 0.10;
    private static final double LOGISTICS_WEIGHT = 0.10;
    private static final double CONFIDENCE_WEIGHT = 0.10;

    private final ObjectMapper objectMapper;
    private final PortfolioProjectRepository projectRepository;
    private final ExternalCertificateRepository certificateRepository;
    private final UserVerifiedSkillRepository verifiedSkillRepository;
    private final StudentSkillVerificationRequestRepository studentVerificationRepository;
    private final GeneratedCVRepository cvRepository;
    private final PortfolioService portfolioService;

    public ScoreResult score(
            PortfolioExtendedProfile profile,
            JobPosting job,
            ShortTermJob shortTermJob,
            CandidateSearchRequest request
    ) {
        CandidateContext context = buildContext(profile, job, shortTermJob, request);

        double skillFit = calculateVerifiedSkillFit(context);
        double yearsFit = calculateExperienceFit(context);
        double seniorityFit = calculateSeniorityFit(context);
        double experienceFit = clamp01((yearsFit * 0.60) + (seniorityFit * 0.40));
        double evidenceFit = calculateEvidenceFit(context);
        double deliveryFit = calculateDeliveryFit(context);
        double logisticsFit = calculateLogisticsFit(context);
        double confidenceFit = calculateConfidenceFit(context);
        double riskPenalty = calculateRiskPenalty(context, skillFit, logisticsFit);

        double rawScore = (skillFit * SKILL_WEIGHT)
                + (experienceFit * EXPERIENCE_WEIGHT)
                + (evidenceFit * EVIDENCE_WEIGHT)
                + (deliveryFit * DELIVERY_WEIGHT)
                + (logisticsFit * LOGISTICS_WEIGHT)
                + (confidenceFit * CONFIDENCE_WEIGHT);
        double overallScore = applyProductionCaps(clamp01(rawScore - riskPenalty), context);
        context.fitVerdict = determineFitVerdict(context, overallScore);
        context.fitSummaryTitle = buildFitSummaryTitle(context);
        context.fitSummaryReason = buildFitSummaryReason(context);
        overallScore = round(overallScore, 4);

        List<CandidateFitAnalysisDTO.ComponentScoreDTO> components = List.of(
                component("skillFit", "Kỹ năng bắt buộc", skillFit, SKILL_WEIGHT, buildSkillExplanation(context)),
                component("experienceFit", "Kinh nghiệm & cấp bậc", experienceFit, EXPERIENCE_WEIGHT, buildExperienceExplanation(context)),
                component("evidenceFit", "Bằng chứng năng lực", evidenceFit, EVIDENCE_WEIGHT, buildEvidenceExplanation(context)),
                component("deliveryFit", "Lịch sử hoàn thành", deliveryFit, DELIVERY_WEIGHT, buildDeliveryExplanation(context)),
                component("logisticsFit", "Ngân sách & điều kiện làm việc", logisticsFit, LOGISTICS_WEIGHT, buildLogisticsExplanation(context)),
                component("confidenceFit", "Độ tin cậy dữ liệu", confidenceFit, CONFIDENCE_WEIGHT, buildConfidenceExplanation(context))
        );

        CandidateFitAnalysisDTO analysis = CandidateFitAnalysisDTO.builder()
                .overallScore(overallScore)
                .band(determineBand(overallScore))
                .recommendation(buildRecommendation(overallScore, context))
                .confidenceScore(round(confidenceFit, 4))
                .riskPenalty(round(riskPenalty, 4))
                .verifiedSkillMatchPercent(round(context.verifiedSkillMatchPercent, 4))
                .evidenceBackedSkillPercent(round(context.evidenceBackedSkillPercent, 4))
                .declaredOnlySkillPercent(round(context.declaredOnlySkillPercent, 4))
                .missingSkillPercent(round(context.missingSkillPercent, 4))
                .requiredSkillSignals(context.requiredSkillSignals)
                .unverifiedSkillWarnings(context.unverifiedSkillWarnings)
                .requiredSeniority(context.requiredSeniority)
                .inferredSeniority(context.inferredSeniority)
                .seniorityPass(context.seniorityPass)
                .overqualified(context.overqualified)
                .seniorityConfidence(round(context.seniorityConfidence, 4))
                .senioritySummary(context.senioritySummary)
                .seniorityDecision(context.seniorityDecision)
                .seniorityRiskLevel(context.seniorityRiskLevel)
                .fitVerdict(context.fitVerdict)
                .fitSummaryTitle(context.fitSummaryTitle)
                .fitSummaryReason(context.fitSummaryReason)
                .seniorityEvidence(context.seniorityEvidence)
                .components(components)
                .skillBreakdown(context.skillBreakdown)
                .evidenceHighlights(context.evidenceHighlights)
                .missingRequirements(context.missingRequirements)
                .riskFlags(context.riskFlags)
                .interviewQuestions(buildInterviewQuestions(context))
                .nextActions(buildNextActions(overallScore, context))
                .build();

        return ScoreResult.builder()
                .overallScore(overallScore)
                .skillFit(round(skillFit, 4))
                .experienceFit(round(experienceFit, 4))
                .evidenceFit(round(evidenceFit, 4))
                .deliveryFit(round(deliveryFit, 4))
                .logisticsFit(round(logisticsFit, 4))
                .confidenceFit(round(confidenceFit, 4))
                .riskPenalty(round(riskPenalty, 4))
                .primarySkillMatch(context.primarySkillMatch)
                .matchedSkills(context.matchedSkills)
                .unmatchedSkills(context.unmatchedSkills)
                .totalRequiredSkills(context.requiredSkills.size())
                .totalCandidateSkills(context.candidateSkills.size())
                .completedMissionsCount(context.completedMissions.size())
                .totalCertificatesCount(context.certificates.size())
                .totalVerifiedSkillsCount(context.verifiedSkills.size() + context.adminVerifiedSkills.size())
                .relevantProjectsCount(context.relevantProjectsCount)
                .relevantCertificatesCount(context.relevantCertificatesCount)
                .relevantMissionsCount(context.relevantMissionsCount)
                .averageMissionRating(context.averageMissionRating)
                .verified(context.isVerified())
                .fitExplanation(buildFitExplanation(overallScore, context, analysis))
                .analysis(analysis)
                .build();
    }

    private CandidateContext buildContext(
            PortfolioExtendedProfile profile,
            JobPosting job,
            ShortTermJob shortTermJob,
            CandidateSearchRequest request
    ) {
        CandidateContext context = new CandidateContext();
        context.profile = profile;
        context.job = job;
        context.shortTermJob = shortTermJob;
        context.request = request;
        context.primarySkill = firstNonBlank(
                shortTermJob != null ? shortTermJob.getPrimarySkill() : null,
                job != null ? job.getPrimarySkill() : null
        );

        Long userId = profile.getUserId();
        context.activeCv = cvRepository.findByUserIdAndIsActiveTrue(userId).orElse(null);
        context.profileDeclaredSkills = parseSkillList(profile.getTopSkills());
        context.cvDeclaredSkills = parseCvSkills(context.activeCv);
        context.candidateSkills = mergeDistinct(context.profileDeclaredSkills, context.cvDeclaredSkills);
        context.candidateSkillIndex = indexByNormalized(context.candidateSkills);
        context.requiredSkills = resolveRequiredSkills(job, shortTermJob, request, context.primarySkill);

        context.projects = loadSafely(() -> projectRepository.findByUserIdOrderByCompletionDateDesc(userId));
        context.certificates = loadSafely(() -> certificateRepository.findByUserIdOrderByIssueDateDesc(userId));
        context.verifiedSkills = loadSafely(() -> verifiedSkillRepository.findByUserIdOrderByVerifiedAtDesc(userId));
        context.adminVerifiedSkills = loadSafely(() -> studentVerificationRepository.findApprovedByUserId(userId));
        context.completedMissions = loadSafely(() -> portfolioService.getPublicCompletedMissions(userId));

        context.projectSkillIndex = buildProjectSkillIndex(context.projects);
        context.possibleProjectSkillIndex = buildPossibleProjectSkillIndex(context.projects);
        context.certificateSkillIndex = buildCertificateSkillIndex(context.certificates);
        context.possibleCertificateSkillIndex = buildPossibleCertificateSkillIndex(context.certificates);
        context.verifiedSkillIndex = buildVerifiedSkillIndex(context.verifiedSkills, context.adminVerifiedSkills);
        context.missionSkillIndex = buildMissionSkillIndex(context.completedMissions);
        context.averageMissionRating = context.completedMissions.stream()
                .map(CompletedMissionDTO::getRating)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
        populateSeniorityAnalysis(context);

        return context;
    }

    private List<String> resolveRequiredSkills(
            JobPosting job,
            ShortTermJob shortTermJob,
            CandidateSearchRequest request,
            String primarySkill
    ) {
        LinkedHashMap<String, String> skills = new LinkedHashMap<>();
        if (primarySkill != null && !primarySkill.isBlank()) {
            addRequiredSkill(skills, primarySkill.trim(), true);
        }

        String rawRequiredSkills = null;
        if (shortTermJob != null) {
            rawRequiredSkills = shortTermJob.getRequiredSkills();
        } else if (job != null) {
            rawRequiredSkills = job.getRequiredSkills();
        } else if (request != null) {
            rawRequiredSkills = request.getSkills();
        }

        parseSkillList(rawRequiredSkills).stream()
                .filter(skill -> !normalize(skill).isBlank())
                .forEach(skill -> addRequiredSkill(skills, skill, false));

        return new ArrayList<>(skills.values());
    }

    private void addRequiredSkill(LinkedHashMap<String, String> skills, String skill, boolean primary) {
        String normalized = normalize(skill);
        if (normalized.isBlank()) {
            return;
        }
        if (primary || !skills.containsKey(normalized)) {
            skills.put(normalized, skill.trim());
        }
    }

    private double calculateVerifiedSkillFit(CandidateContext context) {
        if (context.requiredSkills.isEmpty()) {
            if (context.candidateSkills.isEmpty()) {
                context.riskFlags.add("Ứng viên chưa khai báo kỹ năng nổi bật.");
                return 0.30;
            }
            context.declaredOnlySkillPercent = 1.0;
            return 0.72;
        }

        List<Double> scores = new ArrayList<>();
        int verifiedCount = 0;
        int evidenceCount = 0;
        int declaredOnlyCount = 0;
        int missingCount = 0;

        for (String requiredSkill : context.requiredSkills) {
            SkillMatch match = matchRequiredSkillWithProof(requiredSkill, context);
            scores.add(match.score);

            boolean primary = isSameSkill(requiredSkill, context.primarySkill);
            boolean proofBacked = match.matched
                    && ("VERIFIED".equals(match.verificationStatus) || "EVIDENCE_BACKED".equals(match.verificationStatus));
            if (primary) {
                context.primarySkillMatch = proofBacked;
            }

            if ("VERIFIED".equals(match.verificationStatus)) {
                verifiedCount++;
            } else if ("EVIDENCE_BACKED".equals(match.verificationStatus)) {
                evidenceCount++;
            } else if ("POSSIBLE_EVIDENCE".equals(match.verificationStatus)) {
                context.riskFlags.add("Kỹ năng " + requiredSkill + " chỉ có bằng chứng gián tiếp, cần kiểm tra thêm.");
            } else if ("DECLARED_ONLY".equals(match.verificationStatus)) {
                declaredOnlyCount++;
                context.unverifiedSkillWarnings.add("Ứng viên có tự khai " + requiredSkill
                        + ", nhưng chưa có mentor/admin xác thực hoặc minh chứng portfolio đủ rõ.");
            } else {
                missingCount++;
            }

            if (proofBacked) {
                context.matchedSkills.add(requiredSkill);
            } else if (!"POSSIBLE_EVIDENCE".equals(match.verificationStatus)) {
                context.unmatchedSkills.add(requiredSkill);
                context.missingRequirements.add(CandidateFitAnalysisDTO.MissingRequirementDTO.builder()
                        .skill(requiredSkill)
                        .severity(primary ? "CRITICAL" : "IMPORTANT")
                        .suggestion("Yêu cầu ứng viên bổ sung xác thực mentor/admin, project, chứng chỉ hoặc làm bài test ngắn để chứng minh kỹ năng này.")
                        .build());
            }

            context.requiredSkillSignals.add(CandidateFitAnalysisDTO.RequiredSkillSignalDTO.builder()
                    .skill(requiredSkill)
                    .primary(primary)
                    .status(match.verificationStatus)
                    .confidenceScore(round(match.confidence, 4))
                    .businessMeaning(match.businessMeaning)
                    .sources(match.evidenceSources)
                    .build());

            context.skillBreakdown.add(CandidateFitAnalysisDTO.SkillBreakdownDTO.builder()
                    .skill(requiredSkill)
                    .primary(primary)
                    .required(true)
                    .matched(proofBacked)
                    .matchType(match.matchType)
                    .verificationStatus(match.verificationStatus)
                    .relevanceScore(round(match.score, 4))
                    .confidenceScore(round(match.confidence, 4))
                    .businessMeaning(match.businessMeaning)
                    .evidenceSources(match.evidenceSources)
                    .build());
        }

        int requiredCount = Math.max(1, context.requiredSkills.size());
        context.verifiedSkillMatchPercent = (double) verifiedCount / requiredCount;
        context.evidenceBackedSkillPercent = (double) evidenceCount / requiredCount;
        context.declaredOnlySkillPercent = (double) declaredOnlyCount / requiredCount;
        context.missingSkillPercent = (double) missingCount / requiredCount;

        double averageSkillFit = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (context.primarySkill != null && !context.primarySkill.isBlank() && !context.primarySkillMatch) {
            context.riskFlags.add("Thiếu bằng chứng đáng tin cho kỹ năng chính: " + context.primarySkill + ".");
            averageSkillFit = Math.min(averageSkillFit, 0.72);
        }

        return clamp01(averageSkillFit);
    }

    private SkillMatch matchRequiredSkillWithProof(String requiredSkill, CandidateContext context) {
        String requiredNorm = normalize(requiredSkill);

        SkillEvidence verifiedEvidence = findBestEvidence(requiredNorm, context.verifiedSkillIndex, "Verified skill");
        if (verifiedEvidence != null) {
            return SkillMatch.builder()
                    .matched(true)
                    .score(1.0)
                    .confidence(verifiedEvidence.confidence())
                    .matchType("VERIFIED_SKILL")
                    .verificationStatus("VERIFIED")
                    .businessMeaning("Kỹ năng " + requiredSkill + " đã được mentor/admin xác thực, có thể xem là bằng chứng năng lực chính.")
                    .evidenceSources(new ArrayList<>(List.of(verifiedEvidence.label())))
                    .build();
        }

        List<SkillEvidence> evidence = new ArrayList<>();
        Optional.ofNullable(findBestEvidence(requiredNorm, context.projectSkillIndex, "Project")).ifPresent(evidence::add);
        Optional.ofNullable(findBestEvidence(requiredNorm, context.certificateSkillIndex, "Certificate")).ifPresent(evidence::add);
        Optional.ofNullable(findBestEvidence(requiredNorm, context.missionSkillIndex, "Completed mission")).ifPresent(evidence::add);
        if (!evidence.isEmpty()) {
            double confidence = evidence.stream().mapToDouble(SkillEvidence::confidence).max().orElse(0.70);
            List<String> sources = evidence.stream().map(SkillEvidence::label).distinct().toList();
            return SkillMatch.builder()
                    .matched(true)
                    .score(0.68)
                    .confidence(confidence)
                    .matchType("PORTFOLIO_EVIDENCE")
                    .verificationStatus("EVIDENCE_BACKED")
                    .businessMeaning("Kỹ năng " + requiredSkill + " có bằng chứng trong mission/project/chứng chỉ. Đây là bằng chứng hỗ trợ, nhưng chưa mạnh bằng skill đã xác thực trực tiếp.")
                    .evidenceSources(new ArrayList<>(sources))
                    .build();
        }

        List<SkillEvidence> possibleEvidence = new ArrayList<>();
        Optional.ofNullable(findBestEvidence(requiredNorm, context.possibleProjectSkillIndex, "Possible project evidence")).ifPresent(possibleEvidence::add);
        Optional.ofNullable(findBestEvidence(requiredNorm, context.possibleCertificateSkillIndex, "Possible certificate evidence")).ifPresent(possibleEvidence::add);
        if (!possibleEvidence.isEmpty()) {
            double confidence = possibleEvidence.stream().mapToDouble(SkillEvidence::confidence).max().orElse(0.45);
            List<String> sources = possibleEvidence.stream().map(SkillEvidence::label).distinct().toList();
            return SkillMatch.builder()
                    .matched(true)
                    .score(0.35)
                    .confidence(confidence)
                    .matchType("POSSIBLE_PORTFOLIO_EVIDENCE")
                    .verificationStatus("POSSIBLE_EVIDENCE")
                    .businessMeaning("Kỹ năng " + requiredSkill + " chỉ xuất hiện trong mô tả portfolio, cần phỏng vấn hoặc yêu cầu minh chứng rõ hơn.")
                    .evidenceSources(new ArrayList<>(sources))
                    .build();
        }

        for (String candidateSkill : context.candidateSkills) {
            double similarity = skillSimilarity(requiredSkill, candidateSkill);
            if (similarity >= 0.55) {
                return SkillMatch.builder()
                        .matched(true)
                        .score(0.08)
                        .confidence(Math.min(0.50, similarity))
                        .matchType(similarity >= 0.95 ? "DECLARED_EXACT" : "DECLARED_RELATED")
                        .verificationStatus("DECLARED_ONLY")
                        .businessMeaning("Kỹ năng " + requiredSkill + " chỉ do ứng viên tự khai trong CV/top skills, chưa đủ để chứng minh năng lực.")
                        .evidenceSources(new ArrayList<>(List.of("Ứng viên tự khai trong CV/top skills: " + candidateSkill)))
                        .build();
            }
        }

        return SkillMatch.builder()
                .matched(false)
                .score(0.0)
                .confidence(0.0)
                .matchType("MISSING")
                .verificationStatus("MISSING")
                .businessMeaning("Chưa tìm thấy bằng chứng đáng tin cho kỹ năng " + requiredSkill + ".")
                .evidenceSources(new ArrayList<>())
                .build();
    }

    private double calculateSkillFit(CandidateContext context) {
        if (context.requiredSkills.isEmpty()) {
            if (context.candidateSkills.isEmpty()) {
                context.riskFlags.add("Ứng viên chưa khai báo kỹ năng nổi bật.");
                return 0.30;
            }
            return 0.72;
        }

        List<Double> scores = new ArrayList<>();
        for (String requiredSkill : context.requiredSkills) {
            SkillMatch match = matchRequiredSkillWithProof(requiredSkill, context);
            scores.add(match.score);

            if (isSameSkill(requiredSkill, context.primarySkill)) {
                context.primarySkillMatch = match.matched;
            }

            if (match.matched) {
                context.matchedSkills.add(requiredSkill);
            } else {
                context.unmatchedSkills.add(requiredSkill);
                context.missingRequirements.add(CandidateFitAnalysisDTO.MissingRequirementDTO.builder()
                        .skill(requiredSkill)
                        .severity(isSameSkill(requiredSkill, context.primarySkill) ? "CRITICAL" : "IMPORTANT")
                        .suggestion("Yêu cầu ứng viên cung cấp project, chứng chỉ hoặc bài test chứng minh kỹ năng này.")
                        .build());
            }

            context.skillBreakdown.add(CandidateFitAnalysisDTO.SkillBreakdownDTO.builder()
                    .skill(requiredSkill)
                    .primary(isSameSkill(requiredSkill, context.primarySkill))
                    .required(true)
                    .matched(match.matched)
                    .matchType(match.matchType)
                    .relevanceScore(round(match.score, 4))
                    .confidenceScore(round(match.confidence, 4))
                    .evidenceSources(match.evidenceSources)
                    .build());
        }

        double averageSkillFit = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (context.primarySkill != null && !context.primarySkill.isBlank() && !context.primarySkillMatch) {
            context.riskFlags.add("Thiếu kỹ năng chính: " + context.primarySkill + ".");
            averageSkillFit = Math.min(averageSkillFit, 0.72);
        }

        return clamp01(averageSkillFit);
    }

    private double calculateExperienceFit(CandidateContext context) {
        Integer years = context.profile.getYearsOfExperience();
        int candidateYears = years != null ? Math.max(0, years) : 0;
        int requiredYears = resolveRequiredYears(context);

        if (years == null) {
            return requiredYears <= 1 ? 0.55 : 0.35;
        }
        if (candidateYears >= requiredYears) {
            return 1.0;
        }
        if (candidateYears >= requiredYears * 0.75) {
            return 0.75;
        }
        if (candidateYears >= requiredYears * 0.50) {
            return 0.55;
        }
        return requiredYears <= 1 ? 0.65 : 0.30;
    }

    private double calculateSeniorityFit(CandidateContext context) {
        if (context.job == null || context.requiredSeniority == null) {
            return 1.0;
        }
        return switch (Optional.ofNullable(context.seniorityDecision).orElse("NEEDS_REVIEW")) {
            case "PASS", "NOT_APPLICABLE" -> 1.0;
            case "NEEDS_REVIEW" -> 0.50;
            case "FAIL_OVERQUALIFIED" -> 0.40;
            case "FAIL_UNDERQUALIFIED" -> 0.30;
            default -> context.seniorityPass ? 1.0 : 0.30;
        };
    }

    private void populateSeniorityAnalysis(CandidateContext context) {
        if (context.job == null) {
            context.seniorityDecision = "NOT_APPLICABLE";
            context.seniorityRiskLevel = "LOW";
            context.senioritySummary = "Short-term job khong ap dung seniority gate full-time.";
            return;
        }

        context.requiredSeniority = normalizeSeniority(context.job.getExperienceLevel());
        if (context.requiredSeniority == null) {
            context.seniorityPass = true;
            context.seniorityDecision = "NOT_APPLICABLE";
            context.seniorityRiskLevel = "LOW";
            context.senioritySummary = "Job full-time khong khai bao experience level cu the.";
            return;
        }

        List<String> titleSignals = collectSeniorityTitleSignals(context);
        String titleLevel = titleSignals.stream()
                .map(this::inferSeniorityFromTitle)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(this::seniorityRank))
                .orElse(null);
        String yearsLevel = inferSeniorityFromYears(context.profile.getYearsOfExperience());

        context.inferredSeniority = strongerSeniority(titleLevel, yearsLevel);
        if (titleLevel != null) {
            context.seniorityEvidence.add("Tín hiệu chức danh trong CV/portfolio: " + String.join("; ", titleSignals.stream().limit(4).toList()));
        }
        if (context.profile.getYearsOfExperience() != null) {
            context.seniorityEvidence.add("Số năm kinh nghiệm trong portfolio: " + context.profile.getYearsOfExperience());
        }
        if (context.activeCv != null) {
            context.seniorityEvidence.add("Đã kiểm tra CV active phiên bản " + context.activeCv.getVersion() + ".");
        }

        context.seniorityConfidence = clamp01(
                (titleLevel != null ? 0.55 : 0.0)
                        + (yearsLevel != null ? 0.30 : 0.0)
                        + (!titleSignals.isEmpty() ? 0.15 : 0.0)
        );

        int requiredRank = seniorityRank(context.requiredSeniority);
        int inferredRank = seniorityRank(context.inferredSeniority);
        if (context.inferredSeniority == null) {
            context.seniorityPass = false;
            context.seniorityDecision = "NEEDS_REVIEW";
            context.seniorityRiskLevel = "MEDIUM";
            String reason = "Chưa đủ thông tin CV/portfolio để xác định ứng viên có đúng cấp bậc " + context.requiredSeniority + " hay không.";
            context.riskFlags.add(reason);
            context.senioritySummary = reason;
            return;
        }

        if ("FRESHER".equals(context.requiredSeniority)) {
            context.overqualified = inferredRank > seniorityRank("FRESHER");
            context.seniorityPass = !context.overqualified && inferredRank <= seniorityRank("FRESHER");
            context.seniorityDecision = context.seniorityPass ? "PASS" : "FAIL_OVERQUALIFIED";
        } else {
            context.seniorityPass = inferredRank >= requiredRank;
            context.seniorityDecision = context.seniorityPass ? "PASS" : "FAIL_UNDERQUALIFIED";
        }
        context.seniorityRiskLevel = context.seniorityPass ? "LOW" : "HIGH";

        if (!context.seniorityPass) {
            String reason = context.overqualified
                    ? "Job yêu cầu Fresher nhưng CV/portfolio cho thấy ứng viên đã ở cấp " + context.inferredSeniority + "."
                    : "Job yêu cầu " + context.requiredSeniority + " nhưng CV/portfolio chỉ suy luận được " + context.inferredSeniority + ".";
            context.riskFlags.add(reason);
            context.senioritySummary = reason;
        } else {
            context.senioritySummary = "Seniority phù hợp: job yêu cầu " + context.requiredSeniority
                    + ", CV/portfolio suy luận " + context.inferredSeniority + ".";
        }
    }

    private List<String> collectSeniorityTitleSignals(CandidateContext context) {
        List<String> signals = new ArrayList<>();
        if (notBlank(context.profile.getProfessionalTitle())) {
            signals.add(context.profile.getProfessionalTitle());
        }
        if (context.activeCv != null && notBlank(context.activeCv.getCvJson())) {
            try {
                JsonNode root = objectMapper.readTree(context.activeCv.getCvJson());
                JsonNode title = root.path("personalInfo").path("professionalTitle");
                if (title.isTextual() && notBlank(title.asText())) {
                    signals.add(title.asText());
                }
                JsonNode experience = root.path("experience");
                if (experience.isArray()) {
                    experience.forEach(node -> {
                        JsonNode expTitle = node.path("title");
                        if (expTitle.isTextual() && notBlank(expTitle.asText())) {
                            signals.add(expTitle.asText());
                        }
                    });
                }
            } catch (Exception exception) {
                log.debug("Unable to parse active CV seniority signals: {}", exception.getMessage());
            }
        }
        if (notBlank(context.profile.getWorkExperiences())) {
            try {
                JsonNode experiences = objectMapper.readTree(context.profile.getWorkExperiences());
                if (experiences.isArray()) {
                    experiences.forEach(node -> {
                        JsonNode position = node.path("position");
                        if (position.isTextual() && notBlank(position.asText())) {
                            signals.add(position.asText());
                        }
                    });
                }
            } catch (Exception exception) {
                log.debug("Unable to parse portfolio work experience seniority signals: {}", exception.getMessage());
            }
        }
        return signals.stream().filter(this::notBlank).distinct().toList();
    }

    private String normalizeSeniority(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank() || normalized.contains("all")) return null;
        if (normalized.contains("intern")) return "INTERNSHIP";
        if (normalized.contains("fresher") || normalized.contains("entry")) return "FRESHER";
        if (normalized.contains("junior")) return "JUNIOR";
        if (normalized.contains("middle") || normalized.contains("mid")) return "MIDDLE";
        if (normalized.contains("senior") || normalized.contains("lead") || normalized.contains("principal")) return "SENIOR";
        return null;
    }

    private String inferSeniorityFromTitle(String value) {
        return normalizeSeniority(value);
    }

    private String inferSeniorityFromYears(Integer years) {
        if (years == null) return null;
        if (years <= 0) return "INTERNSHIP";
        if (years <= 1) return "FRESHER";
        if (years <= 2) return "JUNIOR";
        if (years <= 5) return "MIDDLE";
        return "SENIOR";
    }

    private String strongerSeniority(String left, String right) {
        if (left == null) return right;
        if (right == null) return left;
        return seniorityRank(left) >= seniorityRank(right) ? left : right;
    }

    private int seniorityRank(String level) {
        if (level == null) return 0;
        return switch (level) {
            case "INTERNSHIP" -> 0;
            case "FRESHER" -> 1;
            case "JUNIOR" -> 2;
            case "MIDDLE" -> 3;
            case "SENIOR" -> 4;
            default -> 0;
        };
    }

    private double calculateEvidenceFit(CandidateContext context) {
        if (context.requiredSkills.isEmpty()) {
            return clamp01((Math.min(1.0, context.projects.size() / 3.0) * 0.45)
                    + (Math.min(1.0, context.certificates.size() / 2.0) * 0.25)
                    + (Math.min(1.0, context.verifiedSkillIndex.size() / 3.0) * 0.30));
        }

        int requiredCount = context.requiredSkills.size();
        double verifiedCoverage = coverage(context.requiredSkills, context.verifiedSkillIndex.keySet());
        context.relevantProjectsCount = countRelevantProjects(context);
        context.relevantCertificatesCount = countRelevantCertificates(context);

        double projectScore = Math.min(1.0, context.relevantProjectsCount / Math.max(1.0, Math.min(2.0, requiredCount)));
        double certificateScore = Math.min(1.0, context.relevantCertificatesCount / Math.max(1.0, Math.min(2.0, requiredCount)));

        return clamp01((verifiedCoverage * 0.45) + (projectScore * 0.35) + (certificateScore * 0.20));
    }

    private double calculateDeliveryFit(CandidateContext context) {
        context.relevantMissionsCount = countRelevantMissions(context);

        double missionCountScore = Math.min(1.0, context.completedMissions.size() / 5.0);
        double missionRelevanceScore = context.requiredSkills.isEmpty()
                ? missionCountScore
                : Math.min(1.0, context.relevantMissionsCount / Math.max(1.0, Math.min(3.0, context.requiredSkills.size())));
        double ratingScore = context.averageMissionRating != null ? clamp01(context.averageMissionRating / 5.0) : 0.55;
        double portfolioScore = Math.min(1.0, context.projects.size() / 4.0);

        if (context.shortTermJob != null) {
            return clamp01((missionCountScore * 0.35) + (ratingScore * 0.35) + (missionRelevanceScore * 0.30));
        }

        return clamp01((portfolioScore * 0.35) + (missionCountScore * 0.25) + (ratingScore * 0.20) + (missionRelevanceScore * 0.20));
    }

    private double calculateLogisticsFit(CandidateContext context) {
        double availabilityScore = calculateAvailabilityScore(context.profile.getAvailabilityStatus());
        double locationScore = calculateLocationScore(context);
        double budgetScore = calculateBudgetScore(context);
        context.budgetScore = budgetScore;

        return clamp01((availabilityScore * 0.35) + (locationScore * 0.30) + (budgetScore * 0.35));
    }

    private double calculateConfidenceFit(CandidateContext context) {
        int availableSignals = 0;
        int totalSignals = 10;

        if (notBlank(context.profile.getProfessionalTitle())) availableSignals++;
        if (notBlank(context.profile.getBio()) || notBlank(context.profile.getTagline())) availableSignals++;
        if (!context.candidateSkills.isEmpty()) availableSignals++;
        if (context.profile.getYearsOfExperience() != null) availableSignals++;
        if (context.profile.getHourlyRate() != null) availableSignals++;
        if (context.profile.getTotalProjects() != null && context.profile.getTotalProjects() > 0) availableSignals++;
        if (context.profile.getTotalCertificates() != null && context.profile.getTotalCertificates() > 0) availableSignals++;
        if (!context.verifiedSkillIndex.isEmpty()) availableSignals++;
        if (!context.completedMissions.isEmpty()) availableSignals++;
        if (notBlank(context.profile.getGithubUrl()) || notBlank(context.profile.getLinkedinUrl())
                || notBlank(context.profile.getPortfolioWebsiteUrl())) availableSignals++;

        return clamp01((double) availableSignals / totalSignals);
    }

    private double calculateRiskPenalty(CandidateContext context, double skillFit, double logisticsFit) {
        double penalty = 0.0;

        if (context.requiredSkills.size() > 0) {
            double coverage = (double) context.matchedSkills.size() / context.requiredSkills.size();
            if (coverage < 0.40) {
                penalty += 0.10;
                context.riskFlags.add("Khớp dưới 40% kỹ năng yêu cầu.");
            } else if (coverage < 0.60) {
                penalty += 0.05;
                context.riskFlags.add("Khớp kỹ năng còn thấp, cần xác minh thêm.");
            }
        }

        if (context.primarySkill != null && !context.primarySkill.isBlank() && !context.primarySkillMatch) {
            penalty += 0.08;
        }

        if (context.candidateSkills.isEmpty()) {
            penalty += 0.08;
        }

        if (context.projects.isEmpty() && context.certificates.isEmpty() && context.completedMissions.isEmpty()) {
            penalty += 0.07;
            context.riskFlags.add("Thiếu bằng chứng portfolio/chứng chỉ/mission để đối chiếu.");
        }

        if (context.budgetScore < 0.40 || logisticsFit < 0.40) {
            penalty += 0.05;
            context.riskFlags.add("Điều kiện ngân sách hoặc làm việc có thể chưa phù hợp.");
        }

        if (context.job != null && context.requiredSeniority != null && !context.seniorityPass) {
            penalty += context.overqualified ? 0.06 : 0.10;
        }

        if (skillFit >= 0.80 && !context.riskFlags.isEmpty()) {
            penalty *= 0.75;
        }

        return Math.min(0.25, penalty);
    }

    private double applyProductionCaps(double score, CandidateContext context) {
        double capped = score;
        if (context.primarySkill != null && !context.primarySkill.isBlank() && !context.primarySkillMatch) {
            capped = Math.min(capped, 0.72);
            context.riskFlags.add("Tong diem bi gioi han vi skill chinh chua co bang chung xac thuc.");
        }
        if (context.verifiedSkillMatchPercent == 0.0 && context.evidenceBackedSkillPercent < 0.40) {
            capped = Math.min(capped, 0.60);
            context.riskFlags.add("Tong diem bi gioi han vi skill bat buoc chu yeu chua duoc xac thuc.");
        }
        if ("FAIL_OVERQUALIFIED".equals(context.seniorityDecision) || "FAIL_UNDERQUALIFIED".equals(context.seniorityDecision)) {
            capped = Math.min(capped, 0.70);
        }
        if ("NEEDS_REVIEW".equals(context.seniorityDecision)) {
            capped = Math.min(capped, 0.75);
        }
        return clamp01(capped);
    }

    private String determineFitVerdict(CandidateContext context, double overallScore) {
        if ("FAIL_OVERQUALIFIED".equals(context.seniorityDecision)
                || "FAIL_UNDERQUALIFIED".equals(context.seniorityDecision)) {
            return "SENIORITY_RISK";
        }
        if ("NEEDS_REVIEW".equals(context.seniorityDecision)) {
            return "NEEDS_REVIEW";
        }
        if (context.primarySkill != null && !context.primarySkill.isBlank() && !context.primarySkillMatch) {
            return "MISSING_CRITICAL_SKILLS";
        }
        if (context.verifiedSkillMatchPercent >= 0.60 && context.missingSkillPercent <= 0.20 && overallScore >= 0.68) {
            return "STRONG_VERIFIED_FIT";
        }
        if (context.evidenceBackedSkillPercent > 0.0 || context.verifiedSkillMatchPercent > 0.0) {
            return "PARTIAL_EVIDENCE_FIT";
        }
        if (context.declaredOnlySkillPercent > 0.0) {
            return "UNVERIFIED_CLAIM_ONLY";
        }
        return "MISSING_CRITICAL_SKILLS";
    }

    private String buildFitSummaryTitle(CandidateContext context) {
        return switch (Optional.ofNullable(context.fitVerdict).orElse("NEEDS_REVIEW")) {
            case "STRONG_VERIFIED_FIT" -> "Phù hợp mạnh, có kỹ năng đã xác thực";
            case "PARTIAL_EVIDENCE_FIT" -> "Có bằng chứng một phần, nên kiểm tra thêm";
            case "UNVERIFIED_CLAIM_ONLY" -> "Chủ yếu là kỹ năng tự khai";
            case "SENIORITY_RISK" -> "Có rủi ro về cấp bậc";
            case "MISSING_CRITICAL_SKILLS" -> "Thiếu bằng chứng cho kỹ năng quan trọng";
            default -> "Cần kiểm tra thêm trước khi quyết định";
        };
    }

    private String buildFitSummaryReason(CandidateContext context) {
        return "Đã xác thực " + Math.round(context.verifiedSkillMatchPercent * 100)
                + "%, có bằng chứng " + Math.round(context.evidenceBackedSkillPercent * 100)
                + "%, tự khai " + Math.round(context.declaredOnlySkillPercent * 100)
                + "% và còn thiếu " + Math.round(context.missingSkillPercent * 100)
                + "% kỹ năng yêu cầu.";
    }

    private CandidateFitAnalysisDTO.ComponentScoreDTO component(
            String key,
            String label,
            double score,
            double weight,
            String explanation
    ) {
        return CandidateFitAnalysisDTO.ComponentScoreDTO.builder()
                .key(key)
                .label(label)
                .score(round(score, 4))
                .weight(weight)
                .weightedScore(round(score * weight, 4))
                .explanation(explanation)
                .build();
    }

    private String buildSkillExplanation(CandidateContext context) {
        if (context.requiredSkills.isEmpty()) {
            return "Không có kỹ năng mục tiêu cụ thể, dùng chất lượng hồ sơ kỹ năng để ước tính.";
        }
        return "Khớp " + context.matchedSkills.size() + "/" + context.requiredSkills.size()
                + " kỹ năng; thiếu " + context.unmatchedSkills.size() + " kỹ năng.";
    }

    private String buildExperienceExplanation(CandidateContext context) {
        int requiredYears = resolveRequiredYears(context);
        Integer years = context.profile.getYearsOfExperience();
        return "Yêu cầu ước tính " + requiredYears + " năm; ứng viên khai báo "
                + (years != null ? years + " năm." : "chưa có số năm kinh nghiệm.");
    }

    private String buildEvidenceExplanation(CandidateContext context) {
        return context.relevantProjectsCount + " project liên quan, "
                + context.relevantCertificatesCount + " chứng chỉ liên quan, "
                + context.verifiedSkillIndex.size() + " kỹ năng đã xác thực.";
    }

    private String buildDeliveryExplanation(CandidateContext context) {
        String rating = context.averageMissionRating != null
                ? String.format(Locale.US, "%.1f/5", context.averageMissionRating)
                : "chưa có rating";
        return context.completedMissions.size() + " mission/job đã hoàn thành, "
                + context.relevantMissionsCount + " mission liên quan, rating " + rating + ".";
    }

    private String buildLogisticsExplanation(CandidateContext context) {
        return "Đánh giá theo ngân sách, location/remote và trạng thái sẵn sàng làm việc.";
    }

    private String buildConfidenceExplanation(CandidateContext context) {
        return "Độ tin cậy dựa trên mức đầy đủ hồ sơ, skill, evidence, portfolio và dữ liệu hệ thống.";
    }

    private String buildRecommendation(double score, CandidateContext context) {
        if (score >= 0.82) {
            return "Ưu tiên liên hệ. Hồ sơ có độ phù hợp cao và đủ tín hiệu để chuyển sang phỏng vấn.";
        }
        if (score >= 0.68) {
            return "Nên liên hệ. Cần xác minh thêm một vài kỹ năng hoặc điều kiện làm việc.";
        }
        if (score >= 0.52) {
            return "Cân nhắc. Phù hợp một phần, nên dùng bài test hoặc phỏng vấn kỹ thuật ngắn.";
        }
        if (score >= 0.35) {
            return "Chỉ nên giữ làm phương án dự phòng nếu pipeline thiếu ứng viên.";
        }
        return "Không nên ưu tiên ở thời điểm hiện tại vì thiếu nhiều tín hiệu quan trọng.";
    }

    private List<String> buildInterviewQuestions(CandidateContext context) {
        List<String> questions = new ArrayList<>();
        context.unmatchedSkills.stream().limit(3).forEach(skill ->
                questions.add("Bạn đã từng dùng " + skill + " trong dự án nào chưa? Hãy mô tả phạm vi công việc cụ thể."));

        context.matchedSkills.stream().limit(2).forEach(skill ->
                questions.add("Với " + skill + ", bạn có thể chia sẻ project gần nhất và kết quả đạt được không?"));

        if (questions.isEmpty()) {
            questions.add("Bạn hãy chọn project tiêu biểu nhất trong portfolio và giải thích vai trò của mình.");
        }
        return questions.stream().limit(4).collect(Collectors.toList());
    }

    private List<String> buildNextActions(double score, CandidateContext context) {
        List<String> actions = new ArrayList<>();
        if (score >= 0.68) {
            actions.add("Mời ứng viên trao đổi nhanh về kỳ vọng và thời gian bắt đầu.");
        } else {
            actions.add("Gửi bài test ngắn tập trung vào kỹ năng còn thiếu trước khi phỏng vấn sâu.");
        }
        if (!context.unmatchedSkills.isEmpty()) {
            actions.add("Xác minh kỹ năng thiếu: " + String.join(", ", context.unmatchedSkills.stream().limit(3).toList()) + ".");
        }
        if (context.budgetScore < 0.65) {
            actions.add("Trao đổi sớm về ngân sách hoặc mức rate mong muốn.");
        }
        return actions;
    }

    private String buildFitExplanation(double overallScore, CandidateContext context, CandidateFitAnalysisDTO analysis) {
        String band = analysis.getBand() != null ? analysis.getBand() : determineBand(overallScore);
        StringBuilder explanation = new StringBuilder();
        explanation.append("Xếp hạng ").append(band).append(" với ")
                .append(Math.round(overallScore * 100)).append("% tổng phù hợp. ");

        if (!context.requiredSkills.isEmpty()) {
            explanation.append("Khớp ").append(context.matchedSkills.size()).append("/")
                    .append(context.requiredSkills.size()).append(" kỹ năng yêu cầu");
            if (!context.matchedSkills.isEmpty()) {
                explanation.append(" (")
                        .append(String.join(", ", context.matchedSkills.stream().limit(4).toList()))
                        .append(")");
            }
            explanation.append(". ");
        }
        if (!context.unmatchedSkills.isEmpty()) {
            explanation.append("Cần kiểm tra thêm: ")
                    .append(String.join(", ", context.unmatchedSkills.stream().limit(3).toList()))
                    .append(". ");
        }
        explanation.append("Verified skill ")
                .append(Math.round(context.verifiedSkillMatchPercent * 100)).append("%, evidence ")
                .append(Math.round(context.evidenceBackedSkillPercent * 100)).append("%, tu khai ")
                .append(Math.round(context.declaredOnlySkillPercent * 100)).append("%, thieu ")
                .append(Math.round(context.missingSkillPercent * 100)).append("%. ");
        if (context.seniorityDecision != null && !"NOT_APPLICABLE".equals(context.seniorityDecision)) {
            explanation.append("Seniority: ").append(context.seniorityDecision).append(". ");
        }
        explanation.append("Evidence: ").append(context.relevantProjectsCount).append(" project, ")
                .append(context.relevantCertificatesCount).append(" chứng chỉ, ")
                .append(context.relevantMissionsCount).append(" mission liên quan.");
        return explanation.toString();
    }

    private String determineBand(double score) {
        if (score >= 0.82) return "EXCELLENT";
        if (score >= 0.68) return "GOOD";
        if (score >= 0.52) return "FAIR";
        if (score >= 0.35) return "WEAK";
        return "POOR";
    }

    private int resolveRequiredYears(CandidateContext context) {
        String level = null;
        if (context.job != null) {
            level = context.job.getExperienceLevel();
        }
        if (level == null && context.request != null) {
            level = context.request.getExperienceLevel();
        }
        if (level == null && context.shortTermJob != null) {
            return 2;
        }
        if (level == null) {
            return 1;
        }
        String normalized = normalize(level);
        if (normalized.contains("intern") || normalized.contains("entry") || normalized.contains("junior")) return 1;
        if (normalized.contains("senior") || normalized.contains("lead")) return 5;
        if (normalized.contains("expert")) return 8;
        if (normalized.contains("middle") || normalized.contains("mid")) return 3;
        return 2;
    }

    private double calculateAvailabilityScore(String availabilityStatus) {
        if (availabilityStatus == null || availabilityStatus.isBlank()) {
            return 0.55;
        }
        String normalized = normalize(availabilityStatus);
        if (normalized.contains("available") || normalized.contains("san sang") || normalized.contains("dang ranh")) {
            return 1.0;
        }
        if (normalized.contains("busy") || normalized.contains("ban")) {
            return 0.55;
        }
        if (normalized.contains("not") || normalized.contains("khong")) {
            return 0.25;
        }
        return 0.65;
    }

    private double calculateLocationScore(CandidateContext context) {
        Boolean isRemote = null;
        String jobLocation = null;
        if (context.shortTermJob != null) {
            isRemote = context.shortTermJob.getIsRemote();
            jobLocation = context.shortTermJob.getLocation();
        } else if (context.job != null) {
            isRemote = context.job.getIsRemote();
            jobLocation = context.job.getLocation();
        }

        if (Boolean.TRUE.equals(isRemote) || jobLocation == null || jobLocation.isBlank()) {
            return 1.0;
        }

        String candidateLocation = String.join(" ",
                Optional.ofNullable(context.profile.getLocation()).orElse(""),
                Optional.ofNullable(context.profile.getRegion()).orElse(""),
                Optional.ofNullable(context.profile.getAddress()).orElse(""));

        return normalize(candidateLocation).contains(normalize(jobLocation)) ? 1.0 : 0.45;
    }

    private double calculateBudgetScore(CandidateContext context) {
        if (context.profile.getHourlyRate() == null) {
            return 0.55;
        }

        double candidateRate = context.profile.getHourlyRate();
        if (context.shortTermJob != null && context.shortTermJob.getBudget() != null) {
            double estimatedHours = parseEstimatedHours(context.shortTermJob.getEstimatedDuration());
            double impliedRate = context.shortTermJob.getBudget().doubleValue() / Math.max(1.0, estimatedHours);
            return compareRate(candidateRate, impliedRate, impliedRate);
        }

        if (context.job != null && context.job.getMaxBudget() != null) {
            BigDecimal minBudget = context.job.getMinBudget() != null ? context.job.getMinBudget() : BigDecimal.ZERO;
            double hourlyMin = minBudget.doubleValue() / 160.0;
            double hourlyMax = context.job.getMaxBudget().doubleValue() / 160.0;
            return compareRate(candidateRate, hourlyMin, hourlyMax);
        }

        return 0.60;
    }

    private double compareRate(double candidateRate, double minRate, double maxRate) {
        if (candidateRate <= 0 || maxRate <= 0) {
            return 0.55;
        }
        if (candidateRate <= maxRate && candidateRate >= Math.max(0, minRate * 0.45)) {
            return 1.0;
        }
        if (candidateRate < minRate) {
            return 0.85;
        }
        if (candidateRate <= maxRate * 1.20) {
            return 0.70;
        }
        if (candidateRate <= maxRate * 1.50) {
            return 0.42;
        }
        return 0.20;
    }

    private double parseEstimatedHours(String estimatedDuration) {
        if (estimatedDuration == null || estimatedDuration.isBlank()) {
            return 40.0;
        }
        String duration = normalize(estimatedDuration);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([\\d.]+)").matcher(duration);
        double value = matcher.find() ? Double.parseDouble(matcher.group(1)) : 1.0;
        if (duration.contains("hour") || duration.contains("gio")) return value;
        if (duration.contains("day") || duration.contains("ngay")) return value * 8;
        if (duration.contains("week") || duration.contains("tuan")) return value * 40;
        if (duration.contains("month") || duration.contains("thang")) return value * 160;
        return Math.max(1.0, value);
    }

    private int countRelevantProjects(CandidateContext context) {
        int count = 0;
        for (PortfolioProject project : context.projects) {
            List<String> matched = findMatchedSkills(context.requiredSkills, projectText(project));
            if (!matched.isEmpty()) {
                count++;
                context.evidenceHighlights.add(CandidateFitAnalysisDTO.EvidenceHighlightDTO.builder()
                        .type("PROJECT")
                        .title(firstNonBlank(project.getTitle(), "Portfolio project"))
                        .relevanceScore(round(Math.min(1.0, matched.size() / Math.max(1.0, context.requiredSkills.size())), 4))
                        .matchedSkills(matched)
                        .build());
            }
        }
        return count;
    }

    private int countRelevantCertificates(CandidateContext context) {
        int count = 0;
        for (ExternalCertificate certificate : context.certificates) {
            List<String> matched = findMatchedSkills(context.requiredSkills, certificateText(certificate));
            if (!matched.isEmpty()) {
                count++;
                context.evidenceHighlights.add(CandidateFitAnalysisDTO.EvidenceHighlightDTO.builder()
                        .type(Boolean.TRUE.equals(certificate.getIsVerified()) ? "VERIFIED_CERTIFICATE" : "CERTIFICATE")
                        .title(firstNonBlank(certificate.getTitle(), "Certificate"))
                        .relevanceScore(round(Math.min(1.0, matched.size() / Math.max(1.0, context.requiredSkills.size())), 4))
                        .matchedSkills(matched)
                        .build());
            }
        }
        return count;
    }

    private int countRelevantMissions(CandidateContext context) {
        int count = 0;
        for (CompletedMissionDTO mission : context.completedMissions) {
            List<String> missionSkills = mission.getRequiredSkills() != null ? mission.getRequiredSkills() : Collections.emptyList();
            List<String> matched = context.requiredSkills.stream()
                    .filter(required -> missionSkills.stream().anyMatch(skill -> skillSimilarity(required, skill) >= 0.55))
                    .distinct()
                    .toList();
            if (!matched.isEmpty()) {
                count++;
                context.evidenceHighlights.add(CandidateFitAnalysisDTO.EvidenceHighlightDTO.builder()
                        .type("COMPLETED_MISSION")
                        .title(firstNonBlank(mission.getJobTitle(), "Completed mission"))
                        .relevanceScore(round(Math.min(1.0, matched.size() / Math.max(1.0, context.requiredSkills.size())), 4))
                        .matchedSkills(matched)
                        .build());
            }
        }
        return count;
    }

    private List<String> findMatchedSkills(List<String> requiredSkills, String text) {
        String normalizedText = normalize(text);
        return requiredSkills.stream()
                .filter(skill -> containsSkill(normalizedText, skill))
                .distinct()
                .toList();
    }

    private String projectText(PortfolioProject project) {
        List<String> parts = new ArrayList<>();
        parts.add(project.getTitle());
        parts.add(project.getDescription());
        if (project.getTools() != null) parts.addAll(project.getTools());
        if (project.getOutcomes() != null) parts.addAll(project.getOutcomes());
        return joinText(parts);
    }

    private String certificateText(ExternalCertificate certificate) {
        List<String> parts = new ArrayList<>();
        parts.add(certificate.getTitle());
        parts.add(certificate.getDescription());
        parts.add(certificate.getIssuingOrganization());
        if (certificate.getSkills() != null) parts.addAll(certificate.getSkills());
        return joinText(parts);
    }

    private Map<String, List<SkillEvidence>> buildProjectSkillIndex(List<PortfolioProject> projects) {
        Map<String, List<SkillEvidence>> index = new LinkedHashMap<>();
        for (PortfolioProject project : projects) {
            String title = firstNonBlank(project.getTitle(), "Portfolio project");
            if (project.getTools() == null) {
                continue;
            }
            for (String tool : project.getTools()) {
                putEvidence(index, tool, "Project tool: " + title + " (" + tool + ")", 0.78);
            }
        }
        return index;
    }

    private Map<String, List<SkillEvidence>> buildPossibleProjectSkillIndex(List<PortfolioProject> projects) {
        Map<String, List<SkillEvidence>> index = new LinkedHashMap<>();
        for (PortfolioProject project : projects) {
            String title = firstNonBlank(project.getTitle(), "Portfolio project");
            List<String> parts = new ArrayList<>();
            parts.add(project.getTitle());
            parts.add(project.getDescription());
            if (project.getOutcomes() != null) parts.addAll(project.getOutcomes());
            for (String token : extractSkillTokens(joinText(parts))) {
                index.computeIfAbsent(token, key -> new ArrayList<>())
                        .add(new SkillEvidence("Project text: " + title, 0.45));
            }
        }
        return index;
    }

    private Map<String, List<SkillEvidence>> buildCertificateSkillIndex(List<ExternalCertificate> certificates) {
        Map<String, List<SkillEvidence>> index = new LinkedHashMap<>();
        for (ExternalCertificate certificate : certificates) {
            String title = firstNonBlank(certificate.getTitle(), "Certificate");
            if (!Boolean.TRUE.equals(certificate.getIsVerified()) || certificate.getSkills() == null) {
                continue;
            }
            for (String skill : certificate.getSkills()) {
                putEvidence(index, skill, "Verified certificate: " + title + " (" + skill + ")", 0.92);
            }
        }
        return index;
    }

    private Map<String, List<SkillEvidence>> buildPossibleCertificateSkillIndex(List<ExternalCertificate> certificates) {
        Map<String, List<SkillEvidence>> index = new LinkedHashMap<>();
        for (ExternalCertificate certificate : certificates) {
            if (Boolean.TRUE.equals(certificate.getIsVerified())) {
                continue;
            }
            String title = firstNonBlank(certificate.getTitle(), "Certificate");
            if (certificate.getSkills() != null) {
                for (String skill : certificate.getSkills()) {
                    putEvidence(index, skill, "Unverified certificate: " + title + " (" + skill + ")", 0.50);
                }
            }
        }
        return index;
    }

    private Map<String, List<SkillEvidence>> buildVerifiedSkillIndex(
            List<UserVerifiedSkill> verifiedSkills,
            List<StudentSkillVerificationRequest> adminVerifiedSkills
    ) {
        Map<String, List<SkillEvidence>> index = new LinkedHashMap<>();
        for (UserVerifiedSkill skill : verifiedSkills) {
            putEvidence(index, skill.getSkillName(), "Mentor verified: " + humanizeSkill(skill.getSkillName()), 1.0);
        }
        for (StudentSkillVerificationRequest request : adminVerifiedSkills) {
            putEvidence(index, request.getSkillName(), "Admin verified: " + humanizeSkill(request.getSkillName()), 0.95);
        }
        return index;
    }

    private Map<String, List<SkillEvidence>> buildMissionSkillIndex(List<CompletedMissionDTO> missions) {
        Map<String, List<SkillEvidence>> index = new LinkedHashMap<>();
        for (CompletedMissionDTO mission : missions) {
            if (mission.getRequiredSkills() == null) {
                continue;
            }
            String title = firstNonBlank(mission.getJobTitle(), "Completed mission");
            double confidence = mission.getRating() != null ? clamp01(0.55 + (mission.getRating() / 10.0)) : 0.68;
            for (String skill : mission.getRequiredSkills()) {
                putEvidence(index, skill, "Completed mission: " + title, confidence);
            }
        }
        return index;
    }

    private void putEvidence(Map<String, List<SkillEvidence>> index, String skill, String label, double confidence) {
        String normalized = normalize(skill);
        if (normalized.isBlank()) {
            return;
        }
        index.computeIfAbsent(normalized, key -> new ArrayList<>())
                .add(new SkillEvidence(label, confidence));
    }

    private SkillEvidence findBestEvidence(String requiredNorm, Map<String, List<SkillEvidence>> evidenceIndex, String source) {
        return evidenceIndex.entrySet().stream()
                .filter(entry -> skillSimilarity(requiredNorm, entry.getKey()) >= 0.55)
                .flatMap(entry -> entry.getValue().stream())
                .max(Comparator.comparingDouble(SkillEvidence::confidence))
                .map(evidence -> new SkillEvidence(source + " - " + evidence.label(), evidence.confidence()))
                .orElse(null);
    }

    private double coverage(List<String> requiredSkills, Collection<String> evidenceSkills) {
        if (requiredSkills.isEmpty()) {
            return 0.0;
        }
        long covered = requiredSkills.stream()
                .filter(required -> evidenceSkills.stream().anyMatch(skill -> skillSimilarity(required, skill) >= 0.55))
                .count();
        return clamp01((double) covered / requiredSkills.size());
    }

    private Map<String, String> indexByNormalized(List<String> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        this::normalize,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Set<String> extractSkillTokens(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> tokens = new LinkedHashSet<>(Arrays.asList(normalized.split("\\s+")));
        Set<String> phrases = new LinkedHashSet<>();
        List<String> tokenList = tokens.stream().filter(token -> token.length() > 1).toList();
        for (int i = 0; i < tokenList.size(); i++) {
            phrases.add(tokenList.get(i));
            if (i + 1 < tokenList.size()) {
                phrases.add(tokenList.get(i) + " " + tokenList.get(i + 1));
            }
            if (i + 2 < tokenList.size()) {
                phrases.add(tokenList.get(i) + " " + tokenList.get(i + 1) + " " + tokenList.get(i + 2));
            }
        }
        return phrases;
    }

    private boolean containsSkill(String normalizedText, String skill) {
        String normalizedSkill = normalize(skill);
        if (normalizedSkill.isBlank()) {
            return false;
        }
        if (normalizedText.contains(normalizedSkill)) {
            return true;
        }
        return expandAliases(normalizedSkill).stream().anyMatch(normalizedText::contains);
    }

    private double skillSimilarity(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isBlank() || b.isBlank()) {
            return 0.0;
        }
        if (a.equals(b)) {
            return 1.0;
        }

        Set<String> aAliases = expandAliases(a);
        Set<String> bAliases = expandAliases(b);
        if (!Collections.disjoint(aAliases, bAliases)) {
            return 0.92;
        }

        if (a.contains(b) || b.contains(a)) {
            return Math.min(0.86, 0.50 + (Math.min(a.length(), b.length()) / (double) Math.max(a.length(), b.length())));
        }

        Set<String> aTokens = new LinkedHashSet<>(Arrays.asList(a.split("\\s+")));
        Set<String> bTokens = new LinkedHashSet<>(Arrays.asList(b.split("\\s+")));
        Set<String> intersection = new LinkedHashSet<>(aTokens);
        intersection.retainAll(bTokens);
        if (intersection.isEmpty()) {
            return 0.0;
        }
        Set<String> union = new LinkedHashSet<>(aTokens);
        union.addAll(bTokens);
        return Math.min(0.72, (double) intersection.size() / union.size());
    }

    private Set<String> expandAliases(String normalizedSkill) {
        String canonical = normalize(normalizedSkill);
        Map<String, Set<String>> aliases = Map.ofEntries(
                Map.entry("javascript", Set.of("javascript", "js", "ecmascript")),
                Map.entry("typescript", Set.of("typescript", "ts")),
                Map.entry("react", Set.of("react", "reactjs", "react js")),
                Map.entry("node", Set.of("node", "nodejs", "node js")),
                Map.entry("spring boot", Set.of("spring boot", "springboot", "java spring", "spring")),
                Map.entry("java", Set.of("java", "core java", "java se")),
                Map.entry("ui ux", Set.of("ui ux", "ui/ux", "ux ui", "user experience", "user interface")),
                Map.entry("figma", Set.of("figma", "ui design")),
                Map.entry("sql", Set.of("sql", "postgresql", "mysql", "database")),
                Map.entry("python", Set.of("python", "py")),
                Map.entry("machine learning", Set.of("machine learning", "ml", "ai model")),
                Map.entry("docker", Set.of("docker", "container", "containerization")),
                Map.entry("aws", Set.of("aws", "amazon web services")),
                Map.entry("git", Set.of("git", "github", "version control"))
        );

        for (Set<String> values : aliases.values()) {
            if (values.contains(canonical)) {
                return values;
            }
        }
        return Set.of(canonical);
    }

    private boolean isSameSkill(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return skillSimilarity(left, right) >= 0.92;
    }

    private List<String> parseSkillList(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Collections.emptyList();
        }

        try {
            List<String> parsed = objectMapper.readValue(
                    rawValue,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            return parsed.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
        } catch (JsonProcessingException ignored) {
            return Arrays.stream(rawValue.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
        }
    }

    private List<String> parseCvSkills(GeneratedCV activeCv) {
        if (activeCv == null || activeCv.getCvJson() == null || activeCv.getCvJson().isBlank()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> skills = new LinkedHashSet<>();
        try {
            JsonNode root = objectMapper.readTree(activeCv.getCvJson());
            JsonNode skillCategories = root.path("skills");
            if (skillCategories.isArray()) {
                skillCategories.forEach(category -> {
                    JsonNode categorySkills = category.path("skills");
                    if (categorySkills.isArray()) {
                        categorySkills.forEach(skill -> {
                            if (skill.isTextual()) {
                                addSkill(skills, skill.asText());
                            } else {
                                JsonNode name = skill.path("name");
                                if (name.isTextual()) {
                                    addSkill(skills, name.asText());
                                }
                            }
                        });
                    }
                });
            }
            JsonNode projects = root.path("projects");
            if (projects.isArray()) {
                projects.forEach(project -> {
                    JsonNode technologies = project.path("technologies");
                    if (technologies.isArray()) {
                        technologies.forEach(technology -> {
                            if (technology.isTextual()) {
                                addSkill(skills, technology.asText());
                            }
                        });
                    }
                });
            }
        } catch (Exception exception) {
            log.debug("Unable to parse CV skills: {}", exception.getMessage());
        }
        return new ArrayList<>(skills);
    }

    private void addSkill(Set<String> skills, String value) {
        if (value != null && !value.isBlank()) {
            skills.add(value.trim());
        }
    }

    @SafeVarargs
    private final List<String> mergeDistinct(List<String>... values) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (values != null) {
            for (List<String> list : values) {
                if (list != null) {
                    list.stream()
                            .filter(this::notBlank)
                            .map(String::trim)
                            .forEach(merged::add);
                }
            }
        }
        return new ArrayList<>(merged);
    }

    private <T> List<T> loadSafely(SupplierWithException<List<T>> supplier) {
        try {
            List<T> result = supplier.get();
            return result != null ? result : Collections.emptyList();
        } catch (Exception exception) {
            log.debug("Unable to load candidate scoring evidence: {}", exception.getMessage());
            return Collections.emptyList();
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9+#.]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized
                .replace("react js", "react")
                .replace("reactjs", "react")
                .replace("node js", "node")
                .replace("nodejs", "node")
                .replace("ui ux", "ui ux")
                .trim();
    }

    private String humanizeSkill(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("_", " ").trim();
    }

    private String joinText(Collection<String> parts) {
        return parts.stream().filter(this::notBlank).collect(Collectors.joining(" "));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    private record SkillEvidence(String label, double confidence) {
    }

    @Data
    @Builder
    private static class SkillMatch {
        private boolean matched;
        private double score;
        private double confidence;
        private String matchType;
        private String verificationStatus;
        private String businessMeaning;
        private List<String> evidenceSources;
    }

    private static class CandidateContext {
        private PortfolioExtendedProfile profile;
        private JobPosting job;
        private ShortTermJob shortTermJob;
        private CandidateSearchRequest request;
        private GeneratedCV activeCv;
        private String primarySkill;
        private double budgetScore = 0.60;
        private Boolean primarySkillMatch = false;
        private List<String> profileDeclaredSkills = new ArrayList<>();
        private List<String> cvDeclaredSkills = new ArrayList<>();
        private List<String> candidateSkills = new ArrayList<>();
        private Map<String, String> candidateSkillIndex = new LinkedHashMap<>();
        private List<String> requiredSkills = new ArrayList<>();
        private List<PortfolioProject> projects = new ArrayList<>();
        private List<ExternalCertificate> certificates = new ArrayList<>();
        private List<UserVerifiedSkill> verifiedSkills = new ArrayList<>();
        private List<StudentSkillVerificationRequest> adminVerifiedSkills = new ArrayList<>();
        private List<CompletedMissionDTO> completedMissions = new ArrayList<>();
        private Map<String, List<SkillEvidence>> projectSkillIndex = new LinkedHashMap<>();
        private Map<String, List<SkillEvidence>> possibleProjectSkillIndex = new LinkedHashMap<>();
        private Map<String, List<SkillEvidence>> certificateSkillIndex = new LinkedHashMap<>();
        private Map<String, List<SkillEvidence>> possibleCertificateSkillIndex = new LinkedHashMap<>();
        private Map<String, List<SkillEvidence>> verifiedSkillIndex = new LinkedHashMap<>();
        private Map<String, List<SkillEvidence>> missionSkillIndex = new LinkedHashMap<>();
        private List<String> matchedSkills = new ArrayList<>();
        private List<String> unmatchedSkills = new ArrayList<>();
        private List<String> riskFlags = new ArrayList<>();
        private List<CandidateFitAnalysisDTO.SkillBreakdownDTO> skillBreakdown = new ArrayList<>();
        private List<CandidateFitAnalysisDTO.RequiredSkillSignalDTO> requiredSkillSignals = new ArrayList<>();
        private List<CandidateFitAnalysisDTO.EvidenceHighlightDTO> evidenceHighlights = new ArrayList<>();
        private List<CandidateFitAnalysisDTO.MissingRequirementDTO> missingRequirements = new ArrayList<>();
        private List<String> unverifiedSkillWarnings = new ArrayList<>();
        private double verifiedSkillMatchPercent;
        private double evidenceBackedSkillPercent;
        private double declaredOnlySkillPercent;
        private double missingSkillPercent;
        private String requiredSeniority;
        private String inferredSeniority;
        private boolean seniorityPass = true;
        private boolean overqualified = false;
        private double seniorityConfidence = 0.0;
        private String senioritySummary;
        private String seniorityDecision;
        private String seniorityRiskLevel;
        private String fitVerdict;
        private String fitSummaryTitle;
        private String fitSummaryReason;
        private List<String> seniorityEvidence = new ArrayList<>();
        private int relevantProjectsCount;
        private int relevantCertificatesCount;
        private int relevantMissionsCount;
        private Double averageMissionRating;

        private boolean isVerified() {
            return !verifiedSkills.isEmpty()
                    || !adminVerifiedSkills.isEmpty()
                    || certificates.stream().anyMatch(certificate -> Boolean.TRUE.equals(certificate.getIsVerified()));
        }
    }

    @Data
    @Builder
    public static class ScoreResult {
        private double overallScore;
        private double skillFit;
        private double experienceFit;
        private double evidenceFit;
        private double deliveryFit;
        private double logisticsFit;
        private double confidenceFit;
        private double riskPenalty;
        private Boolean primarySkillMatch;
        private List<String> matchedSkills;
        private List<String> unmatchedSkills;
        private int totalRequiredSkills;
        private int totalCandidateSkills;
        private int completedMissionsCount;
        private int totalCertificatesCount;
        private int totalVerifiedSkillsCount;
        private int relevantProjectsCount;
        private int relevantCertificatesCount;
        private int relevantMissionsCount;
        private Double averageMissionRating;
        private boolean verified;
        private String fitExplanation;
        private CandidateFitAnalysisDTO analysis;
    }
}
