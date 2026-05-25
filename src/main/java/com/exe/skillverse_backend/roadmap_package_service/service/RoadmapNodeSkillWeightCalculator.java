package com.exe.skillverse_backend.roadmap_package_service.service;

import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Utility class to calculate skill weights inside a specific node group dynamically
 * based on global skill weights, difficulty level, and requirement types.
 */
public final class RoadmapNodeSkillWeightCalculator {

    private RoadmapNodeSkillWeightCalculator() {}

    public record SkillInput(
            Long skillId,
            Double globalWeightPercent,
            RequirementType requirementType
    ) {}

    public record CalculatedWeight(
            Long skillId,
            Double weightInNode
    ) {}

    public static List<CalculatedWeight> calculate(String nodeDifficulty, List<SkillInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        if (inputs.size() == 1) {
            return List.of(new CalculatedWeight(inputs.get(0).skillId(), 100.0D));
        }

        String diff = nodeDifficulty != null ? nodeDifficulty.trim().toLowerCase() : "medium";
        List<MutableWeight> mutables = new ArrayList<>();
        BigDecimal totalRawWeight = BigDecimal.ZERO;

        for (SkillInput input : inputs) {
            double globalWeight = input.globalWeightPercent() != null ? input.globalWeightPercent() : 10.0D;
            RequirementType reqType = input.requirementType() != null ? input.requirementType().normalized() : RequirementType.REQUIRED;
            
            double reqFactor = getRequirementFactor(diff, reqType);
            BigDecimal raw = BigDecimal.valueOf(globalWeight).multiply(BigDecimal.valueOf(reqFactor));
            
            mutables.add(new MutableWeight(input.skillId(), raw, BigDecimal.ZERO));
            totalRawWeight = totalRawWeight.add(raw);
        }

        if (totalRawWeight.compareTo(BigDecimal.ZERO) <= 0) {
            double equalShare = 100.0D / inputs.size();
            return inputs.stream()
                    .map(i -> new CalculatedWeight(i.skillId(), equalShare))
                    .toList();
        }

        BigDecimal hundred = BigDecimal.valueOf(100);
        BigDecimal roundedTotal = BigDecimal.ZERO;

        for (MutableWeight m : mutables) {
            BigDecimal percent = m.rawWeight
                    .multiply(hundred)
                    .divide(totalRawWeight, 2, RoundingMode.HALF_UP);
            m.weightInNode = percent;
            roundedTotal = roundedTotal.add(percent);
        }

        // Apply Rounding Delta Correction to ensure total sums up to exactly 100.00%
        BigDecimal delta = hundred.subtract(roundedTotal).setScale(2, RoundingMode.HALF_UP);
        if (delta.compareTo(BigDecimal.ZERO) != 0) {
            MutableWeight maxItem = mutables.stream()
                    .max(Comparator.comparing((MutableWeight m) -> m.rawWeight)
                            .thenComparingLong(m -> m.skillId))
                    .orElse(mutables.get(0));
            maxItem.weightInNode = maxItem.weightInNode.add(delta).setScale(2, RoundingMode.HALF_UP);
        }

        return mutables.stream()
                .map(m -> new CalculatedWeight(m.skillId, m.weightInNode.doubleValue()))
                .toList();
    }

    private static double getRequirementFactor(String difficulty, RequirementType type) {
        return switch (difficulty) {
            case "advanced" -> switch (type) {
                case REQUIRED -> 1.5D;
                case IMPORTANT -> 0.8D;
                case NICE_TO_HAVE, OPTIONAL -> 0.3D;
            };
            case "beginner" -> switch (type) {
                case REQUIRED -> 1.0D;
                case IMPORTANT -> 0.8D;
                case NICE_TO_HAVE, OPTIONAL -> 0.5D;
            };
            // Default "medium" or intermediate
            default -> switch (type) {
                case REQUIRED -> 1.2D;
                case IMPORTANT -> 0.7D;
                case NICE_TO_HAVE, OPTIONAL -> 0.4D;
            };
        };
    }

    private static final class MutableWeight {
        private final Long skillId;
        private final BigDecimal rawWeight;
        private BigDecimal weightInNode;

        private MutableWeight(Long skillId, BigDecimal rawWeight, BigDecimal weightInNode) {
            this.skillId = skillId;
            this.rawWeight = rawWeight;
            this.weightInNode = weightInNode;
        }
    }
}
