package com.exe.skillverse_backend.shared.service.impl;

import com.exe.skillverse_backend.shared.dto.SkillDto;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Helper class for testing SkillService functionality
 * This demonstrates the happy path scenarios for skill management
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SkillServiceTestHelper {

    private final SkillService skillService;

    public void demonstrateSkillManagement() {
        log.info("=== Starting Skill Management Demo ===");

        try {
            // 1. Create root skill
            SkillDto programmingSkill = createRootSkill("Programming", 
                "Programming languages and software development");

            // 2. Create child skills
            SkillDto javaSkill = createChildSkill("Java", 
                "Object-oriented programming language", programmingSkill.getId());
            
            createChildSkill("Python", 
                "High-level interpreted programming language", programmingSkill.getId());

            // 3. Create nested skills
            createChildSkill("Spring Framework", 
                "Java enterprise application framework", javaSkill.getId());

            // 4. Test search functionality
            testSearchFunctionality();

            // 5. Test hierarchy operations
            testHierarchyOperations(programmingSkill.getId());

            log.info("=== Skill Management Demo Completed Successfully ===");

        } catch (Exception e) {
            log.error("Demo failed: {}", e.getMessage(), e);
        }
    }

    private SkillDto createRootSkill(String name, String description) {
        SkillDto dto = SkillDto.builder()
                .name(name)
                .description(description)
                .build();

        SkillDto created = skillService.create(dto);
        log.info("Created root skill: {} (ID: {})", created.getName(), created.getId());
        return created;
    }

    private SkillDto createChildSkill(String name, String description, Long parentId) {
        SkillDto dto = SkillDto.builder()
                .name(name)
                .description(description)
                .parentSkillId(parentId)
                .build();

        SkillDto created = skillService.create(dto);
        log.info("Created child skill: {} (ID: {}, Parent: {})", 
                created.getName(), created.getId(), created.getParentSkillId());
        return created;
    }

    private void testSearchFunctionality() {
        log.info("Testing search functionality...");
        
        PageResponse<SkillDto> searchResults = skillService.search("Programming", PageRequest.of(0, 10));
        log.info("Search for 'Programming' found {} results", searchResults.getItems().size());

        PageResponse<SkillDto> suggestResults = skillService.suggestByPrefix("Java", PageRequest.of(0, 5));
        log.info("Suggestions for 'Java' prefix found {} results", suggestResults.getItems().size());
    }

    private void testHierarchyOperations(Long rootSkillId) {
        log.info("Testing hierarchy operations...");
        
        List<SkillDto> children = skillService.listChildren(rootSkillId);
        log.info("Root skill has {} direct children", children.size());

        if (!children.isEmpty()) {
            Long childId = children.get(0).getId();
            List<Long> pathToRoot = skillService.pathToRoot(childId);
            log.info("Path to root for child {}: {}", childId, pathToRoot);
        }
    }

    /**
     * Test method for error scenarios
     */
    public void testErrorScenarios() {
        log.info("=== Testing Error Scenarios ===");

        try {
            // Test not found
            skillService.get(99999L);
        } catch (NotFoundException e) {
            log.info("✓ NotFoundException properly thrown for non-existent skill");
        }

        try {
            // Test duplicate skill creation
            createRootSkill("TestSkill", "Test description");
            SkillDto duplicate2 = SkillDto.builder()
                    .name("TestSkill")
                    .description("Another test description")
                    .build();
            skillService.create(duplicate2);
        } catch (ConflictException e) {
            log.info("✓ ConflictException properly thrown for duplicate skill");
        }

        log.info("=== Error Scenario Testing Completed ===");
    }
}