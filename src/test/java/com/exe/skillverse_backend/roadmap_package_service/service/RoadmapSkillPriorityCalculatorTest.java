package com.exe.skillverse_backend.roadmap_package_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPositionTrackSkill;
import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import com.exe.skillverse_backend.shared.entity.Skill;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoadmapSkillPriorityCalculatorTest {

    @Test
    void calculateUsesRequirementMultiplierAndNormalizesToOneHundredPercent() {
        List<RoadmapSkillPriorityCalculator.SkillPriority> priorities = RoadmapSkillPriorityCalculator.calculate(
                List.of(
                        trackSkill(1L, RequirementType.REQUIRED, 10, 1),
                        trackSkill(2L, RequirementType.REQUIRED, 5, 2),
                        trackSkill(3L, RequirementType.IMPORTANT, 10, 3),
                        trackSkill(4L, RequirementType.NICE_TO_HAVE, 10, 4)
                ),
                skills(1L, 2L, 3L, 4L));

        assertThat(priorities).hasSize(4);
        assertThat(priorities).extracting(RoadmapSkillPriorityCalculator.SkillPriority::effectiveWeight)
                .containsExactly(30D, 15D, 20D, 10D);
        assertThat(priorities).extracting(RoadmapSkillPriorityCalculator.SkillPriority::requirementMultiplier)
                .containsExactly(3D, 3D, 2D, 1D);
        assertThat(sum(priorities)).isEqualTo(100D);
    }

    @Test
    void calculateHandlesFlexibleSkillCountsAndFallbackWeight() {
        assertThat(sum(RoadmapSkillPriorityCalculator.calculate(
                List.of(trackSkill(1L, RequirementType.REQUIRED, null, 1)),
                skills(1L))))
                .isEqualTo(100D);

        assertThat(sum(RoadmapSkillPriorityCalculator.calculate(
                List.of(
                        trackSkill(1L, RequirementType.REQUIRED, 0, 1),
                        trackSkill(2L, RequirementType.IMPORTANT, -5, 2)),
                skills(1L, 2L))))
                .isEqualTo(100D);

        List<JobPositionTrackSkill> twelveSkills = java.util.stream.LongStream.rangeClosed(1, 12)
                .mapToObj(id -> trackSkill(id, RequirementType.IMPORTANT, 1, (int) id))
                .toList();
        assertThat(sum(RoadmapSkillPriorityCalculator.calculate(twelveSkills, skills(1L, 2L))))
                .isEqualTo(100D);
    }

    @Test
    void calculateRoundingDeltaIsAppliedSoTotalRemainsOneHundred() {
        List<RoadmapSkillPriorityCalculator.SkillPriority> priorities = RoadmapSkillPriorityCalculator.calculate(
                List.of(
                        trackSkill(1L, RequirementType.IMPORTANT, 1, 1),
                        trackSkill(2L, RequirementType.IMPORTANT, 1, 2),
                        trackSkill(3L, RequirementType.IMPORTANT, 1, 3)),
                skills(1L, 2L, 3L));

        assertThat(sum(priorities)).isEqualTo(100D);
        assertThat(priorities).extracting(RoadmapSkillPriorityCalculator.SkillPriority::weightPercent)
                .containsExactly(33.34D, 33.33D, 33.33D);
    }

    private JobPositionTrackSkill trackSkill(Long skillId, RequirementType requirementType, Integer weight, int sortOrder) {
        return JobPositionTrackSkill.builder()
                .skillId(skillId)
                .requirementType(requirementType)
                .weight(weight)
                .sortOrder(sortOrder)
                .build();
    }

    private Map<Long, Skill> skills(Long... ids) {
        return java.util.Arrays.stream(ids)
                .collect(java.util.stream.Collectors.toMap(
                        id -> id,
                        id -> Skill.builder().id(id).name("Skill " + id).canonicalKey("skill-" + id).build()));
    }

    private double sum(List<RoadmapSkillPriorityCalculator.SkillPriority> priorities) {
        return priorities.stream()
                .map(RoadmapSkillPriorityCalculator.SkillPriority::weightPercent)
                .mapToDouble(Double::doubleValue)
                .sum();
    }
}
