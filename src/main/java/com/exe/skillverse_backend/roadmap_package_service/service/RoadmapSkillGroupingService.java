package com.exe.skillverse_backend.roadmap_package_service.service;

import com.exe.skillverse_backend.ai_search_service.config.AISearchConfig;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPositionTrackSkill;
import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import com.exe.skillverse_backend.career_taxonomy_service.repository.JobPositionTrackSkillRepository;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateAutoGroupRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateSkillBlockRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateNodeGroupResponse;
import com.exe.skillverse_backend.shared.entity.Skill;
import com.exe.skillverse_backend.shared.repository.SkillRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoadmapSkillGroupingService {

    private static final int PREFERRED_MAX_SKILLS_PER_NODE = 5;
    private static final int HARD_MAX_SKILLS_PER_NODE = 7;

    private final JobPositionTrackSkillRepository trackSkillRepository;
    private final SkillRepository skillRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private AISearchConfig aiSearchConfig;

    @Autowired(required = false)
    @Qualifier("aiSearchRestTemplate")
    private RestTemplate aiSearchRestTemplate;

    public List<RoadmapTemplateNodeGroupResponse> autoGroup(RoadmapTemplateAutoGroupRequest request) {
        List<SkillCandidate> candidates = resolveCandidates(request);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<RoadmapTemplateNodeGroupResponse> aiGroups = tryAiSearchGrouping(request, candidates);
        if (!aiGroups.isEmpty()) {
            return aiGroups;
        }

        Map<String, List<SkillCandidate>> buckets = new LinkedHashMap<>();
        for (SkillCandidate candidate : candidates) {
            buckets.computeIfAbsent(resolveBucket(candidate), ignored -> new ArrayList<>()).add(candidate);
        }

        List<List<SkillCandidate>> groups = splitLargeBuckets(new ArrayList<>(buckets.values()));
        groups = rebalanceToTarget(groups, Math.max(1, request.getTotalNodeCount()));

        List<RoadmapTemplateNodeGroupResponse> responses = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            List<SkillCandidate> group = groups.get(i);
            String title = resolveModuleTitle(group, i + 1);
            responses.add(RoadmapTemplateNodeGroupResponse.builder()
                    .nodeKey("module-" + (i + 1))
                    .title(title)
                    .description("Learning module that combines related skills: " + group.stream()
                            .map(SkillCandidate::skillName)
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining(", ")))
                    .difficulty(resolveDifficulty(group))
                    .estimatedHours(Math.max(3D, group.size() * 2D))
                    .expectedOutput("Complete a practical artifact that demonstrates " + title + ".")
                    .rubric("The submission applies the module skills together and can be reviewed against completion criteria.")
                    .skills(group.stream().map(this::toSkillItem).toList())
                    .build());
        }
        return responses;
    }

    private List<RoadmapTemplateNodeGroupResponse> tryAiSearchGrouping(
            RoadmapTemplateAutoGroupRequest request,
            List<SkillCandidate> candidates) {
        if (aiSearchConfig == null || aiSearchRestTemplate == null
                || !aiSearchConfig.isEnabled()
                || aiSearchConfig.getApiKey() == null
                || aiSearchConfig.getApiKey().isBlank()) {
            return List.of();
        }
        try {
            String prompt = buildAiGroupingPrompt(request, candidates);
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", aiSearchConfig.getModel());
            payload.put("temperature", aiSearchConfig.getTemperature());
            payload.put("max_tokens", Math.min(aiSearchConfig.getMaxTokens(), 8000));
            payload.put("messages", List.of(
                    Map.of("role", "system", "content",
                            "You create roadmap learning modules. Return valid JSON only. Never create new skills."),
                    Map.of("role", "user", "content", prompt)));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(aiSearchConfig.getApiKey());
            String response = aiSearchRestTemplate.postForObject(
                    aiSearchConfig.getBaseUrl(),
                    new HttpEntity<>(payload, headers),
                    String.class);
            List<RoadmapTemplateNodeGroupResponse> parsed = parseAiGroupingResponse(response, candidates);
            parsed = repairAiGrouping(parsed, request, candidates);
            if (isValidAiGrouping(parsed, request, candidates)) {
                return parsed;
            }
            log.warn("AI Search grouping response failed validation; using rule-based fallback");
        } catch (Exception ex) {
            log.warn("AI Search grouping failed; using rule-based fallback: {}", ex.getMessage());
        }
        return List.of();
    }

    private String buildAiGroupingPrompt(RoadmapTemplateAutoGroupRequest request, List<SkillCandidate> candidates) {
        String skillsJson;
        try {
            skillsJson = objectMapper.writeValueAsString(candidates.stream()
                    .map(candidate -> Map.of(
                            "skillId", candidate.skillId(),
                            "name", candidate.skillName(),
                            "canonicalKey", firstNonBlank(candidate.canonicalKey(), ""),
                            "requirementType", candidate.requirementType().name(),
                            "weight", candidate.weight(),
                            "sortOrder", candidate.sortOrder()))
                    .toList());
        } catch (Exception ex) {
            skillsJson = "[]";
        }
        return """
                Group these skills into roadmap learning modules for a job-position track.
                Rules:
                - Return JSON only: {"nodeGroups":[{"title":"...","description":"...","skillIds":[1,2],"learningObjectives":["..."],"difficulty":"easy|medium|hard","estimatedHours":4}]}
                - total nodeGroups must equal %d.
                - Each module should combine related skills; do not make 1 skill = 1 node unless unavoidable.
                - Use only skillIds from the input. Do not create new skills.
                - ALL skills from the input list (including REQUIRED, IMPORTANT, NICE_TO_HAVE, and OPTIONAL) MUST be covered without exception. Every single skillId from the input list must appear in the skillIds list of exactly one node group. Do not omit any skill.
                Skills: %s
                """.formatted(Math.max(1, request.getTotalNodeCount()), skillsJson);
    }

    private List<RoadmapTemplateNodeGroupResponse> parseAiGroupingResponse(
            String response,
            List<SkillCandidate> candidates) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode content = root.path("choices").isArray() && !root.path("choices").isEmpty()
                ? root.path("choices").get(0).path("message").path("content")
                : root;
        JsonNode payload = content.isTextual() ? objectMapper.readTree(stripJsonFence(content.asText())) : content;
        JsonNode groupsNode = payload.path("nodeGroups");
        if (!groupsNode.isArray()) {
            return List.of();
        }
        Map<Long, SkillCandidate> byId = candidates.stream()
                .collect(Collectors.toMap(SkillCandidate::skillId, Function.identity(), (a, b) -> a));
        List<RoadmapTemplateNodeGroupResponse> result = new ArrayList<>();
        int index = 1;
        for (JsonNode groupNode : groupsNode) {
            List<RoadmapTemplateNodeGroupResponse.SkillItem> skills = new ArrayList<>();
            JsonNode skillIds = groupNode.path("skillIds");
            if (skillIds.isArray()) {
                for (JsonNode skillIdNode : skillIds) {
                    SkillCandidate candidate = byId.get(skillIdNode.asLong());
                    if (candidate != null) {
                        skills.add(toSkillItem(candidate));
                    }
                }
            }
            String title = firstNonBlank(groupNode.path("title").asText(null), "Learning Module " + index);
            result.add(RoadmapTemplateNodeGroupResponse.builder()
                    .nodeKey("module-" + index)
                    .title(title)
                    .description(groupNode.path("description").asText(null))
                    .learningObjectives(groupNode.path("learningObjectives").isArray()
                            ? joinTextArray(groupNode.path("learningObjectives"))
                            : null)
                    .difficulty(firstNonBlank(groupNode.path("difficulty").asText(null), "medium"))
                    .estimatedHours(groupNode.path("estimatedHours").isNumber()
                            ? groupNode.path("estimatedHours").asDouble()
                            : Math.max(3D, skills.size() * 2D))
                    .expectedOutput("Complete a practical artifact that demonstrates " + title + ".")
                    .rubric("The submission applies the module skills together and can be reviewed against completion criteria.")
                    .completionCriteria("The learner can explain and apply all skills in this module in one practical artifact.")
                    .orderIndex(index)
                    .skills(skills)
                    .build());
            index += 1;
        }
        return result;
    }

    private boolean isValidAiGrouping(
            List<RoadmapTemplateNodeGroupResponse> groups,
            RoadmapTemplateAutoGroupRequest request,
            List<SkillCandidate> candidates) {
        if (groups.size() != Math.max(1, request.getTotalNodeCount())) {
            return false;
        }
        Set<Long> allowed = candidates.stream().map(SkillCandidate::skillId).collect(Collectors.toSet());
        Set<Long> covered = new LinkedHashSet<>();
        int singleSkillCount = 0;
        for (RoadmapTemplateNodeGroupResponse group : groups) {
            if (group.getSkills() == null || group.getSkills().isEmpty() || group.getSkills().size() > HARD_MAX_SKILLS_PER_NODE) {
                return false;
            }
            if (group.getSkills().size() == 1) {
                singleSkillCount += 1;
            }
            for (RoadmapTemplateNodeGroupResponse.SkillItem skill : group.getSkills()) {
                if (skill.getSkillId() == null || !allowed.contains(skill.getSkillId())) {
                    return false;
                }
                covered.add(skill.getSkillId());
            }
        }
        boolean missingAnySkill = candidates.stream()
                .anyMatch(candidate -> !covered.contains(candidate.skillId()));
        return !missingAnySkill && singleSkillCount < Math.ceil(groups.size() * 0.7);
    }

    List<RoadmapTemplateNodeGroupResponse> repairAiGrouping(
            List<RoadmapTemplateNodeGroupResponse> groups,
            RoadmapTemplateAutoGroupRequest request,
            List<SkillCandidate> candidates) {
        if (groups == null || groups.isEmpty()) {
            return groups;
        }

        Set<Long> allowedIds = candidates.stream().map(SkillCandidate::skillId).collect(Collectors.toSet());
        Map<Long, SkillCandidate> candidateMap = candidates.stream()
                .collect(Collectors.toMap(SkillCandidate::skillId, Function.identity(), (a, b) -> a));

        // 1. Filter out hallucinated skill IDs
        for (RoadmapTemplateNodeGroupResponse group : groups) {
            if (group.getSkills() != null) {
                List<RoadmapTemplateNodeGroupResponse.SkillItem> validSkills = group.getSkills().stream()
                        .filter(s -> s.getSkillId() != null && allowedIds.contains(s.getSkillId()))
                        .collect(Collectors.toCollection(ArrayList::new));
                group.setSkills(validSkills);
            } else {
                group.setSkills(new ArrayList<>());
            }
        }

        // 2. Identify missing skills
        Set<Long> coveredIds = groups.stream()
                .flatMap(g -> g.getSkills().stream())
                .map(RoadmapTemplateNodeGroupResponse.SkillItem::getSkillId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<SkillCandidate> missedCandidates = candidates.stream()
                .filter(c -> !coveredIds.contains(c.skillId()))
                .collect(Collectors.toList());

        if (missedCandidates.isEmpty()) {
            return groups; // Perfect coverage!
        }

        log.info("🛠️ Auto-repairing AI grouping: Found {} missed skills to backfill", missedCandidates.size());

        // 3. Intelligently map each missed skill to the best matching AI node group
        for (SkillCandidate missed : missedCandidates) {
            String missedBucket = resolveBucket(missed);
            RoadmapTemplateNodeGroupResponse bestGroup = null;
            int bestScore = -1000;

            for (RoadmapTemplateNodeGroupResponse group : groups) {
                int score = 0;
                for (RoadmapTemplateNodeGroupResponse.SkillItem skillItem : group.getSkills()) {
                    SkillCandidate groupSkill = candidateMap.get(skillItem.getSkillId());
                    if (groupSkill != null && Objects.equals(resolveBucket(groupSkill), missedBucket)) {
                        score += 15; // Semantic match weight
                    }
                }
                score -= group.getSkills().size() * 2; // Balance penalty

                if (score > bestScore) {
                    bestScore = score;
                    bestGroup = group;
                }
            }

            if (bestGroup == null) {
                bestGroup = groups.get(0);
            }

            List<RoadmapTemplateNodeGroupResponse.SkillItem> updatedSkills = new ArrayList<>(bestGroup.getSkills());
            updatedSkills.add(toSkillItem(missed));
            bestGroup.setSkills(updatedSkills);
            log.info("Successfully repaired: assigned missed skill '{}' into AI node '{}'", 
                    missed.skillName(), bestGroup.getTitle());
        }

        return groups;
    }

    private List<SkillCandidate> resolveCandidates(RoadmapTemplateAutoGroupRequest request) {
        Map<Long, RoadmapTemplateSkillBlockRequest> blocksBySkill = request.getSkillBlocks() == null
                ? Map.of()
                : request.getSkillBlocks().stream()
                .filter(block -> block.getSkillId() != null)
                .collect(Collectors.toMap(
                        RoadmapTemplateSkillBlockRequest::getSkillId,
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new));

        List<JobPositionTrackSkill> trackSkills = trackSkillRepository
                .findByTrackIdOrderBySortOrderAsc(request.getJobPositionTrackId());
        Set<Long> skillIds = new LinkedHashSet<>();
        trackSkills.stream().map(JobPositionTrackSkill::getSkillId).filter(Objects::nonNull).forEach(skillIds::add);
        skillIds.addAll(blocksBySkill.keySet());
        Map<Long, Skill> skillsById = skillRepository.findAllById(skillIds).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity(), (a, b) -> a));

        Map<Long, JobPositionTrackSkill> trackBySkill = trackSkills.stream()
                .filter(item -> item.getSkillId() != null)
                .collect(Collectors.toMap(JobPositionTrackSkill::getSkillId, Function.identity(), (a, b) -> a));

        return skillIds.stream()
                .map(skillId -> {
                    RoadmapTemplateSkillBlockRequest block = blocksBySkill.get(skillId);
                    JobPositionTrackSkill trackSkill = trackBySkill.get(skillId);
                    Skill skill = skillsById.get(skillId);
                    String name = firstNonBlank(
                            block != null ? block.getSkillNameSnapshot() : null,
                            skill != null ? skill.getName() : null,
                            "Skill " + skillId);
                    String canonicalKey = firstNonBlank(
                            block != null ? block.getSkillCanonicalKeySnapshot() : null,
                            skill != null ? skill.getCanonicalKey() : null);
                    RequirementType requirementType = trackSkill != null && trackSkill.getRequirementType() != null
                            ? trackSkill.getRequirementType().normalized()
                            : RequirementType.REQUIRED;
                    int trackWeight = RoadmapSkillPriorityCalculator.normalizeTrackWeight(
                            trackSkill != null ? trackSkill.getWeight() : null);
                    double weight = RoadmapSkillPriorityCalculator.effectiveWeight(requirementType, trackWeight);
                    int sortOrder = trackSkill != null && trackSkill.getSortOrder() != null ? trackSkill.getSortOrder() : 0;
                    return new SkillCandidate(skillId, name, canonicalKey, requirementType, weight, sortOrder);
                })
                .sorted(Comparator
                        .comparingInt((SkillCandidate item) -> requirementRank(item.requirementType()))
                        .thenComparing(Comparator.comparingDouble(SkillCandidate::weight).reversed())
                        .thenComparingInt(SkillCandidate::sortOrder))
                .toList();
    }

    private List<List<SkillCandidate>> splitLargeBuckets(List<List<SkillCandidate>> buckets) {
        List<List<SkillCandidate>> groups = new ArrayList<>();
        for (List<SkillCandidate> bucket : buckets) {
            for (int i = 0; i < bucket.size(); i += PREFERRED_MAX_SKILLS_PER_NODE) {
                groups.add(new ArrayList<>(bucket.subList(i, Math.min(bucket.size(), i + PREFERRED_MAX_SKILLS_PER_NODE))));
            }
        }
        return groups;
    }

    private List<List<SkillCandidate>> rebalanceToTarget(List<List<SkillCandidate>> groups, int target) {
        List<List<SkillCandidate>> result = groups.stream().map(ArrayList::new).collect(Collectors.toCollection(ArrayList::new));
        while (result.size() < target) {
            int splitIndex = largestSplittableGroupIndex(result);
            if (splitIndex < 0) {
                break;
            }
            List<SkillCandidate> source = result.remove(splitIndex);
            int middle = Math.max(1, source.size() / 2);
            result.add(splitIndex, new ArrayList<>(source.subList(0, middle)));
            result.add(splitIndex + 1, new ArrayList<>(source.subList(middle, source.size())));
        }
        while (result.size() > target) {
            int mergeIndex = smallestMergeIndex(result);
            if (mergeIndex < 0) {
                break;
            }
            result.get(mergeIndex).addAll(result.remove(mergeIndex + 1));
        }
        return result;
    }

    private int largestSplittableGroupIndex(List<List<SkillCandidate>> groups) {
        int bestIndex = -1;
        int bestSize = 1;
        for (int i = 0; i < groups.size(); i++) {
            int size = groups.get(i).size();
            if (size > bestSize) {
                bestSize = size;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private int smallestMergeIndex(List<List<SkillCandidate>> groups) {
        int bestIndex = -1;
        int bestSize = Integer.MAX_VALUE;
        for (int i = 0; i + 1 < groups.size(); i++) {
            int combined = groups.get(i).size() + groups.get(i + 1).size();
            if (combined <= HARD_MAX_SKILLS_PER_NODE && combined < bestSize) {
                bestSize = combined;
                bestIndex = i;
            }
        }
        return bestIndex >= 0 ? bestIndex : groups.size() > 1 ? groups.size() - 2 : -1;
    }

    private RoadmapTemplateNodeGroupResponse.SkillItem toSkillItem(SkillCandidate candidate) {
        return RoadmapTemplateNodeGroupResponse.SkillItem.builder()
                .skillId(candidate.skillId())
                .skillName(candidate.skillName())
                .canonicalKey(candidate.canonicalKey())
                .requirementType(candidate.requirementType())
                .build();
    }

    private String resolveBucket(SkillCandidate candidate) {
        String value = (candidate.skillName() + " " + firstNonBlank(candidate.canonicalKey(), ""))
                .toLowerCase(Locale.ROOT);
        if (matches(value, "git", "maven", "gradle", "environment", "tooling")) return "tooling";
        if (matches(value, "java", "oop", "object")) return "java-core";
        if (matches(value, "spring boot", "spring mvc", "mvc architecture")) return "spring-foundation";
        if (matches(value, "rest", "api", "postman", "exception")) return "api-backend";
        if (matches(value, "sql", "postgres", "mysql", "jpa", "database", "persistence")) return "database";
        if (matches(value, "security", "auth", "jwt", "authorization")) return "security";
        if (matches(value, "html", "css", "javascript", "typescript")) return "frontend-foundation";
        if (matches(value, "react", "hook", "router")) return "react-core";
        if (matches(value, "axios", "state management", "redux", "zustand")) return "react-integration";
        if (matches(value, "junit", "test", "testing")) return "testing";
        if (matches(value, "docker", "linux", "compose")) return "runtime";
        if (matches(value, "ci", "cd", "github action", "nginx", "microservice", "deploy")) return "deployment";
        return "general-" + Math.abs(value.hashCode() % 4);
    }

    private String resolveModuleTitle(List<SkillCandidate> group, int index) {
        String joined = group.stream()
                .map(item -> (item.skillName() + " " + firstNonBlank(item.canonicalKey(), "")).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
        if (matches(joined, "git", "maven", "gradle", "environment")) return "Development Workflow & Tooling";
        if (matches(joined, "java", "oop")) return "Java Core & OOP";
        if (matches(joined, "spring boot", "spring mvc")) return "Spring Boot Foundation";
        if (matches(joined, "rest", "api", "postman", "exception")) return "REST API & Backend Practices";
        if (matches(joined, "sql", "postgres", "jpa", "database")) return "Database & Persistence";
        if (matches(joined, "security", "auth", "jwt")) return "Authentication & Security";
        if (matches(joined, "html", "css", "javascript", "typescript")) return "Frontend Foundation";
        if (matches(joined, "react", "hook", "router")) return "React Core Development";
        if (matches(joined, "axios", "state management")) return "React State & API Integration";
        if (matches(joined, "junit", "test")) return "Testing Basics";
        if (matches(joined, "docker", "linux", "compose")) return "Docker & Runtime Environment";
        if (matches(joined, "ci", "cd", "github action", "nginx", "microservice", "deploy")) {
            return "Deployment & Advanced Backend Introduction";
        }
        return "Learning Module " + index + ": " + group.stream()
                .map(SkillCandidate::skillName)
                .filter(Objects::nonNull)
                .limit(2)
                .collect(Collectors.joining(" & "));
    }

    private String resolveDifficulty(List<SkillCandidate> group) {
        boolean hasNiceToHave = group.stream().anyMatch(item -> item.requirementType() == RequirementType.NICE_TO_HAVE);
        boolean allRequired = group.stream().allMatch(item -> item.requirementType() == RequirementType.REQUIRED);
        if (hasNiceToHave && !allRequired) {
            return "hard";
        }
        return group.size() <= 2 ? "easy" : "medium";
    }

    private boolean matches(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private int requirementRank(RequirementType type) {
        RequirementType normalized = type != null ? type.normalized() : RequirementType.REQUIRED;
        return switch (normalized) {
            case REQUIRED -> 0;
            case IMPORTANT -> 1;
            case NICE_TO_HAVE, OPTIONAL -> 2;
        };
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String stripJsonFence(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private String joinTextArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (item != null && item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        });
        return String.join("\n", values);
    }

    record SkillCandidate(
            Long skillId,
            String skillName,
            String canonicalKey,
            RequirementType requirementType,
            double weight,
            int sortOrder
    ) {
    }
}
