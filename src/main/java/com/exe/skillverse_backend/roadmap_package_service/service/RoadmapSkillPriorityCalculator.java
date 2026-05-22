package com.exe.skillverse_backend.roadmap_package_service.service;

import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPositionTrackSkill;
import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import com.exe.skillverse_backend.shared.entity.Skill;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class RoadmapSkillPriorityCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private RoadmapSkillPriorityCalculator() {
    }

    public static List<SkillPriority> calculate(List<JobPositionTrackSkill> trackSkills, Map<Long, Skill> skillsById) {
        if (trackSkills == null || trackSkills.isEmpty()) {
            return List.of();
        }

        List<MutablePriority> mutable = new ArrayList<>();
        for (JobPositionTrackSkill trackSkill : trackSkills) {
            if (trackSkill == null || trackSkill.getSkillId() == null) {
                continue;
            }
            RequirementType requirementType = normalizeRequirementType(trackSkill.getRequirementType());
            int trackWeight = normalizeTrackWeight(trackSkill.getWeight());
            double multiplier = requirementMultiplier(requirementType);
            double effectiveWeight = effectiveWeight(requirementType, trackWeight);
            Skill skill = skillsById != null ? skillsById.get(trackSkill.getSkillId()) : null;
            mutable.add(new MutablePriority(
                    trackSkill.getSkillId(),
                    skill != null ? skill.getName() : null,
                    skill != null ? skill.getCanonicalKey() : null,
                    requirementType,
                    trackWeight,
                    multiplier,
                    effectiveWeight,
                    trackSkill.getSortOrder() != null ? trackSkill.getSortOrder() : 0,
                    BigDecimal.ZERO));
        }
        if (mutable.isEmpty()) {
            return List.of();
        }

        BigDecimal totalEffectiveWeight = mutable.stream()
                .map(item -> BigDecimal.valueOf(item.effectiveWeight()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalEffectiveWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        BigDecimal roundedTotal = BigDecimal.ZERO;
        for (MutablePriority item : mutable) {
            BigDecimal percent = BigDecimal.valueOf(item.effectiveWeight())
                    .multiply(ONE_HUNDRED)
                    .divide(totalEffectiveWeight, 2, RoundingMode.HALF_UP);
            item.setWeightPercent(percent);
            roundedTotal = roundedTotal.add(percent);
        }

        BigDecimal roundingDelta = ONE_HUNDRED.subtract(roundedTotal).setScale(2, RoundingMode.HALF_UP);
        if (roundingDelta.compareTo(BigDecimal.ZERO) != 0) {
            MutablePriority target = mutable.stream()
                    .max(Comparator.comparingDouble(MutablePriority::effectiveWeight)
                            .thenComparingInt(item -> -item.sortOrder()))
                    .orElse(mutable.get(0));
            target.setWeightPercent(target.weightPercent().add(roundingDelta).setScale(2, RoundingMode.HALF_UP));
        }

        return mutable.stream()
                .map(item -> new SkillPriority(
                        item.skillId(),
                        item.skillName(),
                        item.canonicalKey(),
                        item.requirementType(),
                        item.trackWeight(),
                        item.requirementMultiplier(),
                        item.effectiveWeight(),
                        item.weightPercent().doubleValue(),
                        item.sortOrder()))
                .toList();
    }

    public static RequirementType normalizeRequirementType(RequirementType requirementType) {
        return requirementType != null ? requirementType.normalized() : RequirementType.REQUIRED;
    }

    public static int normalizeTrackWeight(Integer weight) {
        return weight != null && weight > 0 ? weight : 1;
    }

    public static double requirementMultiplier(RequirementType requirementType) {
        return switch (normalizeRequirementType(requirementType)) {
            case REQUIRED -> 3D;
            case IMPORTANT -> 2D;
            case NICE_TO_HAVE, OPTIONAL -> 1D;
        };
    }

    public static double effectiveWeight(RequirementType requirementType, Integer trackWeight) {
        return normalizeTrackWeight(trackWeight) * requirementMultiplier(requirementType);
    }

    public record SkillPriority(
            Long skillId,
            String skillName,
            String canonicalKey,
            RequirementType requirementType,
            Integer trackWeight,
            Double requirementMultiplier,
            Double effectiveWeight,
            Double weightPercent,
            Integer sortOrder
    ) {
    }

    private static final class MutablePriority {
        private final Long skillId;
        private final String skillName;
        private final String canonicalKey;
        private final RequirementType requirementType;
        private final Integer trackWeight;
        private final Double requirementMultiplier;
        private final Double effectiveWeight;
        private final Integer sortOrder;
        private BigDecimal weightPercent;

        private MutablePriority(
                Long skillId,
                String skillName,
                String canonicalKey,
                RequirementType requirementType,
                Integer trackWeight,
                Double requirementMultiplier,
                Double effectiveWeight,
                Integer sortOrder,
                BigDecimal weightPercent) {
            this.skillId = skillId;
            this.skillName = skillName;
            this.canonicalKey = canonicalKey;
            this.requirementType = requirementType;
            this.trackWeight = trackWeight;
            this.requirementMultiplier = requirementMultiplier;
            this.effectiveWeight = effectiveWeight;
            this.sortOrder = sortOrder;
            this.weightPercent = weightPercent;
        }

        private Long skillId() {
            return skillId;
        }

        private String skillName() {
            return skillName;
        }

        private String canonicalKey() {
            return canonicalKey;
        }

        private RequirementType requirementType() {
            return requirementType;
        }

        private Integer trackWeight() {
            return trackWeight;
        }

        private Double requirementMultiplier() {
            return requirementMultiplier;
        }

        private Double effectiveWeight() {
            return effectiveWeight;
        }

        private Integer sortOrder() {
            return sortOrder;
        }

        private BigDecimal weightPercent() {
            return weightPercent;
        }

        private void setWeightPercent(BigDecimal value) {
            this.weightPercent = value;
        }
    }
}
