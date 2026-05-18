package com.exe.skillverse_backend.mentor_matching_service.service.impl;

import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.mentor_matching_service.dto.MentorTeachingEligibilityResponse;
import com.exe.skillverse_backend.mentor_matching_service.enums.TeachingEligibilityStatus;
import com.exe.skillverse_backend.mentor_matching_service.service.MentorTeachingEligibilityService;
import com.exe.skillverse_backend.mentor_verification_service.entity.MentorSkillVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.repository.MentorSkillVerificationRequestRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MentorTeachingEligibilityServiceImpl implements MentorTeachingEligibilityService {

    private final MentorSkillVerificationRequestRepository verificationRequestRepository;
    private final RoadmapSessionRepository roadmapSessionRepository;
    private final JourneyRepository journeyRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public MentorTeachingEligibilityResponse evaluateRoadmap(Long mentorId, Long roadmapSessionId, String nodeId) {
        RoadmapSession session = roadmapSessionRepository.findById(roadmapSessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Roadmap session not found: " + roadmapSessionId));
        return evaluate(mentorId, roadmapSessionId, null, session.getRoadmapJson(), nodeId);
    }

    @Override
    @Transactional(readOnly = true)
    public MentorTeachingEligibilityResponse evaluateJourney(Long mentorId, Long journeyId, String nodeId) {
        Journey journey = journeyRepository.findById(journeyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Journey not found: " + journeyId));
        if (journey.getRoadmapSessionId() == null) {
            return needsReviewResponse(mentorId, null, journeyId, nodeId, "Journey has no roadmap session");
        }
        RoadmapSession session = roadmapSessionRepository.findById(journey.getRoadmapSessionId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Roadmap session not found: " + journey.getRoadmapSessionId()));
        return evaluate(mentorId, session.getId(), journeyId, session.getRoadmapJson(), nodeId);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertCanTeachBooking(Long mentorId, Long journeyId, String nodeId, String bookingType) {
        if (journeyId == null || bookingType == null || "GENERAL".equals(bookingType)) {
            return;
        }
        if (!"NODE_MENTORING".equals(bookingType)
                && !"JOURNEY_MENTORING".equals(bookingType)
                && !"ROADMAP_MENTORING".equals(bookingType)) {
            return;
        }

        MentorTeachingEligibilityResponse response = evaluateJourney(mentorId, journeyId, nodeId);
        List<MentorTeachingEligibilityResponse.NodeEligibility> nodes = defaultList(response.getNodes());
        if ("NODE_MENTORING".equals(bookingType) && nodeId != null) {
            nodes.stream()
                    .filter(node -> Objects.equals(node.getNodeId(), nodeId))
                    .findFirst()
                    .filter(node -> node.getStatus() == TeachingEligibilityStatus.NOT_ELIGIBLE)
                    .ifPresent(node -> {
                        throw new ApiException(ErrorCode.CONFLICT,
                                "Mentor is missing required verified skills for this node");
                    });
            return;
        }

        boolean hasEvaluatedSkillNode = nodes.stream()
                .anyMatch(node -> node.getStatus() != TeachingEligibilityStatus.NEEDS_REVIEW);
        boolean hasTeachReadyNode = nodes.stream()
                .anyMatch(node -> node.getStatus() == TeachingEligibilityStatus.ELIGIBLE
                        || node.getStatus() == TeachingEligibilityStatus.PARTIALLY_ELIGIBLE);
        if (hasEvaluatedSkillNode && !hasTeachReadyNode) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Mentor is missing required verified skills for this roadmap");
        }
    }

    private MentorTeachingEligibilityResponse evaluate(
            Long mentorId,
            Long roadmapSessionId,
            Long journeyId,
            String roadmapJson,
            String nodeId) {
        if (roadmapJson == null || roadmapJson.isBlank()) {
            return needsReviewResponse(mentorId, roadmapSessionId, journeyId, nodeId, "Roadmap has no JSON");
        }

        Set<String> verifiedSkillKeys = verifiedSkillKeys(mentorId);
        List<MentorTeachingEligibilityResponse.NodeEligibility> nodes = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(roadmapJson);
            JsonNode roadmap = root.isArray() ? root : root.path("roadmap");
            if (!roadmap.isArray()) {
                return needsReviewResponse(mentorId, roadmapSessionId, journeyId, nodeId, "Roadmap JSON has no roadmap array");
            }
            for (JsonNode nodeJson : roadmap) {
                String currentNodeId = readText(nodeJson, "id");
                if (nodeId != null && !Objects.equals(nodeId, currentNodeId)) {
                    continue;
                }
                nodes.add(evaluateNode(nodeJson, verifiedSkillKeys));
            }
        } catch (Exception e) {
            return needsReviewResponse(mentorId, roadmapSessionId, journeyId, nodeId, "Roadmap JSON cannot be parsed");
        }

        TeachingEligibilityStatus summaryStatus = summarize(nodes);
        int overallMatch = nodes.isEmpty()
                ? 0
                : (int) Math.round(nodes.stream()
                        .map(MentorTeachingEligibilityResponse.NodeEligibility::getMatchPercent)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0));

        return MentorTeachingEligibilityResponse.builder()
                .mentorId(mentorId)
                .roadmapSessionId(roadmapSessionId)
                .journeyId(journeyId)
                .summaryStatus(summaryStatus)
                .overallMatchPercent(overallMatch)
                .nodes(nodes)
                .build();
    }

    private MentorTeachingEligibilityResponse.NodeEligibility evaluateNode(JsonNode nodeJson, Set<String> verifiedSkillKeys) {
        List<MentorTeachingEligibilityResponse.SkillRequirement> requirements = parseRequirements(nodeJson);
        if (requirements.isEmpty()) {
            return MentorTeachingEligibilityResponse.NodeEligibility.builder()
                    .nodeId(readText(nodeJson, "id"))
                    .title(readText(nodeJson, "title"))
                    .status(TeachingEligibilityStatus.NEEDS_REVIEW)
                    .matchPercent(0)
                    .matchedSkills(List.of())
                    .missingRequiredSkills(List.of())
                    .missingImportantSkills(List.of())
                    .missingNiceToHaveSkills(List.of())
                    .build();
        }

        List<MentorTeachingEligibilityResponse.SkillRequirement> matched = new ArrayList<>();
        List<MentorTeachingEligibilityResponse.SkillRequirement> missingRequired = new ArrayList<>();
        List<MentorTeachingEligibilityResponse.SkillRequirement> missingImportant = new ArrayList<>();
        List<MentorTeachingEligibilityResponse.SkillRequirement> missingNice = new ArrayList<>();

        for (MentorTeachingEligibilityResponse.SkillRequirement requirement : requirements) {
            boolean hasSkill = matchesVerifiedSkill(requirement, verifiedSkillKeys);
            if (hasSkill) {
                matched.add(requirement);
                continue;
            }
            RequirementType type = normalize(requirement.getRequirementType());
            if (type == RequirementType.REQUIRED) {
                missingRequired.add(requirement);
            } else if (type == RequirementType.IMPORTANT) {
                missingImportant.add(requirement);
            } else {
                missingNice.add(requirement);
            }
        }

        int matchPercent = computeMatchPercent(requirements, matched);
        TeachingEligibilityStatus status;
        if (!missingRequired.isEmpty()) {
            status = TeachingEligibilityStatus.NOT_ELIGIBLE;
        } else if (missingImportant.isEmpty() || importantCoverage(requirements, matched) >= 0.6) {
            status = TeachingEligibilityStatus.ELIGIBLE;
        } else {
            status = TeachingEligibilityStatus.PARTIALLY_ELIGIBLE;
        }

        return MentorTeachingEligibilityResponse.NodeEligibility.builder()
                .nodeId(readText(nodeJson, "id"))
                .title(readText(nodeJson, "title"))
                .status(status)
                .matchPercent(matchPercent)
                .matchedSkills(matched)
                .missingRequiredSkills(missingRequired)
                .missingImportantSkills(missingImportant)
                .missingNiceToHaveSkills(missingNice)
                .build();
    }

    private List<MentorTeachingEligibilityResponse.SkillRequirement> parseRequirements(JsonNode nodeJson) {
        JsonNode skills = firstPresent(nodeJson, "skills", "skill_requirements", "skillRequirements", "nodeSkills");
        List<MentorTeachingEligibilityResponse.SkillRequirement> requirements = new ArrayList<>();
        if (skills != null && skills.isArray()) {
            for (JsonNode skill : skills) {
                MentorTeachingEligibilityResponse.SkillRequirement requirement = parseRequirement(skill);
                if (requirement != null) {
                    requirements.add(requirement);
                }
            }
        }
        if (!requirements.isEmpty()) {
            return requirements;
        }

        Long legacySkillId = readLong(nodeJson, "skill_id", "skillId");
        String legacySkillName = readText(nodeJson, "skill_name", "skillName");
        if (legacySkillId == null && legacySkillName == null) {
            return List.of();
        }
        return List.of(MentorTeachingEligibilityResponse.SkillRequirement.builder()
                .skillId(legacySkillId)
                .skillName(legacySkillName)
                .canonicalKey(readText(nodeJson, "canonical_key", "canonicalKey"))
                .requirementType(RequirementType.REQUIRED)
                .build());
    }

    private MentorTeachingEligibilityResponse.SkillRequirement parseRequirement(JsonNode skill) {
        Long skillId = readLong(skill, "skill_id", "skillId");
        String skillName = readText(skill, "skill_name", "skillName", "name");
        String canonicalKey = readText(skill, "canonical_key", "canonicalKey");
        if (skillId == null && skillName == null && canonicalKey == null) {
            return null;
        }
        return MentorTeachingEligibilityResponse.SkillRequirement.builder()
                .skillId(skillId)
                .skillName(skillName)
                .canonicalKey(canonicalKey)
                .requirementType(RequirementType.fromValue(readText(skill, "requirement_type", "requirementType", "importance")))
                .build();
    }

    private int computeMatchPercent(
            List<MentorTeachingEligibilityResponse.SkillRequirement> requirements,
            List<MentorTeachingEligibilityResponse.SkillRequirement> matched) {
        double total = groupWeight(requirements, RequirementType.REQUIRED)
                + groupWeight(requirements, RequirementType.IMPORTANT)
                + groupWeight(requirements, RequirementType.NICE_TO_HAVE);
        if (total <= 0) {
            return 0;
        }
        double score = matchedGroupScore(requirements, matched, RequirementType.REQUIRED, 60)
                + matchedGroupScore(requirements, matched, RequirementType.IMPORTANT, 30)
                + matchedGroupScore(requirements, matched, RequirementType.NICE_TO_HAVE, 10);
        return Math.max(0, Math.min(100, (int) Math.round((score / total) * 100)));
    }

    private double matchedGroupScore(
            List<MentorTeachingEligibilityResponse.SkillRequirement> requirements,
            List<MentorTeachingEligibilityResponse.SkillRequirement> matched,
            RequirementType type,
            int weight) {
        long totalInGroup = requirements.stream().filter(req -> normalize(req.getRequirementType()) == type).count();
        if (totalInGroup == 0) {
            return 0;
        }
        long matchedInGroup = matched.stream().filter(req -> normalize(req.getRequirementType()) == type).count();
        return weight * (matchedInGroup / (double) totalInGroup);
    }

    private double groupWeight(List<MentorTeachingEligibilityResponse.SkillRequirement> requirements, RequirementType type) {
        return requirements.stream().anyMatch(req -> normalize(req.getRequirementType()) == type)
                ? switch (type) {
                    case REQUIRED -> 60;
                    case IMPORTANT -> 30;
                    case NICE_TO_HAVE, OPTIONAL -> 10;
                }
                : 0;
    }

    private double importantCoverage(
            List<MentorTeachingEligibilityResponse.SkillRequirement> requirements,
            List<MentorTeachingEligibilityResponse.SkillRequirement> matched) {
        long totalImportant = requirements.stream()
                .filter(req -> normalize(req.getRequirementType()) == RequirementType.IMPORTANT)
                .count();
        if (totalImportant == 0) {
            return 1;
        }
        long matchedImportant = matched.stream()
                .filter(req -> normalize(req.getRequirementType()) == RequirementType.IMPORTANT)
                .count();
        return matchedImportant / (double) totalImportant;
    }

    private TeachingEligibilityStatus summarize(List<MentorTeachingEligibilityResponse.NodeEligibility> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return TeachingEligibilityStatus.NEEDS_REVIEW;
        }
        boolean anyReady = nodes.stream().anyMatch(node -> node.getStatus() == TeachingEligibilityStatus.ELIGIBLE
                || node.getStatus() == TeachingEligibilityStatus.PARTIALLY_ELIGIBLE);
        boolean allEligible = nodes.stream().allMatch(node -> node.getStatus() == TeachingEligibilityStatus.ELIGIBLE);
        boolean allNeedsReview = nodes.stream().allMatch(node -> node.getStatus() == TeachingEligibilityStatus.NEEDS_REVIEW);
        if (allNeedsReview) {
            return TeachingEligibilityStatus.NEEDS_REVIEW;
        }
        if (allEligible) {
            return TeachingEligibilityStatus.ELIGIBLE;
        }
        if (anyReady) {
            return TeachingEligibilityStatus.PARTIALLY_ELIGIBLE;
        }
        return TeachingEligibilityStatus.NOT_ELIGIBLE;
    }

    private MentorTeachingEligibilityResponse needsReviewResponse(
            Long mentorId,
            Long roadmapSessionId,
            Long journeyId,
            String nodeId,
            String reason) {
        MentorTeachingEligibilityResponse.NodeEligibility node = MentorTeachingEligibilityResponse.NodeEligibility.builder()
                .nodeId(nodeId)
                .title(reason)
                .status(TeachingEligibilityStatus.NEEDS_REVIEW)
                .matchPercent(0)
                .matchedSkills(List.of())
                .missingRequiredSkills(List.of())
                .missingImportantSkills(List.of())
                .missingNiceToHaveSkills(List.of())
                .build();
        return MentorTeachingEligibilityResponse.builder()
                .mentorId(mentorId)
                .roadmapSessionId(roadmapSessionId)
                .journeyId(journeyId)
                .summaryStatus(TeachingEligibilityStatus.NEEDS_REVIEW)
                .overallMatchPercent(0)
                .nodes(List.of(node))
                .build();
    }

    private Set<String> verifiedSkillKeys(Long mentorId) {
        return verificationRequestRepository.findApprovedByMentorId(mentorId).stream()
                .map(MentorSkillVerificationRequest::getSkillName)
                .filter(Objects::nonNull)
                .flatMap(skill -> List.of(normalizeMatchKey(skill), skill.trim().toUpperCase(Locale.ROOT)).stream())
                .filter(value -> !value.isBlank())
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    private boolean matchesVerifiedSkill(
            MentorTeachingEligibilityResponse.SkillRequirement requirement,
            Set<String> verifiedSkillKeys) {
        return candidateKeys(requirement).stream().anyMatch(verifiedSkillKeys::contains);
    }

    private Set<String> candidateKeys(MentorTeachingEligibilityResponse.SkillRequirement requirement) {
        Set<String> keys = new LinkedHashSet<>();
        addKey(keys, requirement.getSkillName());
        addKey(keys, requirement.getCanonicalKey());
        return keys;
    }

    private void addKey(Set<String> keys, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        keys.add(raw.trim().toUpperCase(Locale.ROOT));
        keys.add(normalizeMatchKey(raw));
    }

    private String normalizeMatchKey(String raw) {
        return raw == null ? "" : raw.replaceAll("[^a-zA-Z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private RequirementType normalize(RequirementType type) {
        return type == null ? RequirementType.REQUIRED : type.normalized();
    }

    private JsonNode firstPresent(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String readText(JsonNode node, String... keys) {
        JsonNode value = firstPresent(node, keys);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private Long readLong(JsonNode node, String... keys) {
        JsonNode value = firstPresent(node, keys);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isIntegralNumber()) {
            return value.asLong();
        }
        if (value.isNumber()) {
            return Math.round(value.asDouble());
        }
        if (value.isTextual()) {
            try {
                return Long.parseLong(value.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private <T> List<T> defaultList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
