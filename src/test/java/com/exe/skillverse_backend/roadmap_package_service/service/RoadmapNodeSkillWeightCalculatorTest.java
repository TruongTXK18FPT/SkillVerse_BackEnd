package com.exe.skillverse_backend.roadmap_package_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import java.util.List;
import org.junit.jupiter.api.Test;

public class RoadmapNodeSkillWeightCalculatorTest {

    @Test
    public void testSingleSkillCalculation() {
        List<RoadmapNodeSkillWeightCalculator.SkillInput> inputs = List.of(
                new RoadmapNodeSkillWeightCalculator.SkillInput(1L, 40.0D, RequirementType.REQUIRED)
        );

        List<RoadmapNodeSkillWeightCalculator.CalculatedWeight> results = 
                RoadmapNodeSkillWeightCalculator.calculate("advanced", inputs);

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).skillId());
        assertEquals(100.0D, results.get(0).weightInNode());
    }

    @Test
    public void testMultipleSkillsAdvancedCalculation() {
        List<RoadmapNodeSkillWeightCalculator.SkillInput> inputs = List.of(
                new RoadmapNodeSkillWeightCalculator.SkillInput(1L, 40.0D, RequirementType.REQUIRED),  // Spring Boot
                new RoadmapNodeSkillWeightCalculator.SkillInput(2L, 20.0D, RequirementType.IMPORTANT), // Redis
                new RoadmapNodeSkillWeightCalculator.SkillInput(3L, 10.0D, RequirementType.OPTIONAL)    // Docker
        );

        List<RoadmapNodeSkillWeightCalculator.CalculatedWeight> results = 
                RoadmapNodeSkillWeightCalculator.calculate("advanced", inputs);

        assertEquals(3, results.size());
        
        // Sum must be exactly 100.00%
        double sum = results.stream().mapToDouble(RoadmapNodeSkillWeightCalculator.CalculatedWeight::weightInNode).sum();
        assertEquals(100.00D, sum, 0.001D);

        // Spring Boot should have dominant weight (~75.95%)
        RoadmapNodeSkillWeightCalculator.CalculatedWeight springBoot = results.stream().filter(r -> r.skillId() == 1L).findFirst().orElseThrow();
        assertEquals(75.95D, springBoot.weightInNode(), 0.01D);

        // Redis should have intermediate weight (~20.25%)
        RoadmapNodeSkillWeightCalculator.CalculatedWeight redis = results.stream().filter(r -> r.skillId() == 2L).findFirst().orElseThrow();
        assertEquals(20.25D, redis.weightInNode(), 0.01D);

        // Docker should have tiny weight (~3.80%)
        RoadmapNodeSkillWeightCalculator.CalculatedWeight docker = results.stream().filter(r -> r.skillId() == 3L).findFirst().orElseThrow();
        assertEquals(3.80D, docker.weightInNode(), 0.01D);
    }

    @Test
    public void testRoundingCorrection() {
        // Inputs that would produce rounding repeating decimals
        List<RoadmapNodeSkillWeightCalculator.SkillInput> inputs = List.of(
                new RoadmapNodeSkillWeightCalculator.SkillInput(1L, 33.33D, RequirementType.REQUIRED),
                new RoadmapNodeSkillWeightCalculator.SkillInput(2L, 33.33D, RequirementType.REQUIRED),
                new RoadmapNodeSkillWeightCalculator.SkillInput(3L, 33.33D, RequirementType.REQUIRED)
        );

        List<RoadmapNodeSkillWeightCalculator.CalculatedWeight> results = 
                RoadmapNodeSkillWeightCalculator.calculate("medium", inputs);

        double sum = results.stream().mapToDouble(RoadmapNodeSkillWeightCalculator.CalculatedWeight::weightInNode).sum();
        assertEquals(100.00D, sum, 0.001D);
    }

    @Test
    public void testFallbackEmptyWeights() {
        List<RoadmapNodeSkillWeightCalculator.SkillInput> inputs = List.of(
                new RoadmapNodeSkillWeightCalculator.SkillInput(1L, 0.0D, RequirementType.OPTIONAL),
                new RoadmapNodeSkillWeightCalculator.SkillInput(2L, 0.0D, RequirementType.OPTIONAL)
        );

        List<RoadmapNodeSkillWeightCalculator.CalculatedWeight> results = 
                RoadmapNodeSkillWeightCalculator.calculate("medium", inputs);

        assertEquals(2, results.size());
        assertEquals(50.0D, results.get(0).weightInNode());
        assertEquals(50.0D, results.get(1).weightInNode());
    }
}
