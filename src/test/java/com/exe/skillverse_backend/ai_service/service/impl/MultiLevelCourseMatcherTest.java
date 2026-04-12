package com.exe.skillverse_backend.ai_service.service.impl;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.service.AiCourseCatalogService;
import com.exe.skillverse_backend.ai_service.service.TaxonomyService;
import com.exe.skillverse_backend.ai_service.service.dto.CourseCatalogEntry;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiLevelCourseMatcherTest {

    @Mock
    private AiCourseCatalogService catalogService;

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private TaxonomyService taxonomyService;

    private MultiLevelCourseMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new MultiLevelCourseMatcher(catalogService, moduleRepository, taxonomyService);
        when(taxonomyService.detectDomain(anyString(), any(), any())).thenReturn("IT");
    }

    @Test
    @DisplayName("matchNodesToCoursesAndModules should use SKILL_BASED limit")
    void matchNodesToCoursesAndModules_ShouldUseSkillBasedLimit() {
        when(catalogService.preSelectCourses(anyString(), anyInt(), any())).thenReturn(List.of());

        List<RoadmapResponse.RoadmapNode> nodes = List.of(node("n1", "Java backend basics"));

        matcher.matchNodesToCoursesAndModules(nodes, "Backend", "SKILL_BASED", "Java", null);

        verify(catalogService).preSelectCourses(anyString(), eq(MultiLevelCourseMatcher.SKILL_BASED_LIMIT), isNull());
    }

    @Test
    @DisplayName("matchNodesToCoursesAndModules should distribute modules by prerequisite order")
    void matchNodesToCoursesAndModules_ShouldDistributeModulesByPrerequisiteOrder() {
        CourseCatalogEntry course = catalog(100L, "Java Backend", "backend", "beginner", "learn backend java");

        when(catalogService.preSelectCourses(anyString(), anyInt(), any())).thenReturn(List.of(course));
        when(catalogService.getModuleIds(100L)).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(catalogService.getModulePrerequisiteIds(100L, 1L)).thenReturn(List.of());
        when(catalogService.getModulePrerequisiteIds(100L, 2L)).thenReturn(List.of(1L));
        when(catalogService.getModulePrerequisiteIds(100L, 3L)).thenReturn(List.of(2L));
        when(catalogService.getModulePrerequisiteIds(100L, 4L)).thenReturn(List.of(3L));

        when(moduleRepository.findModuleCoursePairsByIds(anyCollection())).thenReturn(List.<Object[]>of(
            new Object[]{1L, 100L},
            new Object[]{2L, 100L},
            new Object[]{3L, 100L},
            new Object[]{4L, 100L}
        ));

        RoadmapResponse.RoadmapNode node1 = node("n1", "Java backend foundations");
        RoadmapResponse.RoadmapNode node2 = node("n2", "Java backend practice");

        matcher.matchNodesToCoursesAndModules(
                List.of(node1, node2),
                "Backend",
                "CAREER_BASED",
                null,
                "Backend Developer"
        );

        assertEquals(List.of("100"), node1.getSuggestedCourseIds());
        assertEquals(List.of("100"), node2.getSuggestedCourseIds());
        assertEquals(List.of("1", "2"), node1.getSuggestedModuleIds());
        assertEquals(List.of("3", "4"), node2.getSuggestedModuleIds());
    }

    @Test
    @DisplayName("matchNodesToCoursesAndModules should strip invalid module IDs")
    void matchNodesToCoursesAndModules_ShouldStripInvalidModuleIds() {
        CourseCatalogEntry course = catalog(200L, "Node Backend", "backend", "beginner", "node backend");

        when(catalogService.preSelectCourses(anyString(), anyInt(), any())).thenReturn(List.of(course));
        when(catalogService.getModuleIds(200L)).thenReturn(List.of(11L, 12L));
        when(catalogService.getModulePrerequisiteIds(200L, 11L)).thenReturn(List.of());
        when(catalogService.getModulePrerequisiteIds(200L, 12L)).thenReturn(List.of());

        when(moduleRepository.findModuleCoursePairsByIds(anyCollection())).thenReturn(List.<Object[]>of(
            new Object[]{11L, 200L}
        ));

        RoadmapResponse.RoadmapNode node = node("n1", "Node backend roadmap");

        matcher.matchNodesToCoursesAndModules(List.of(node), "Backend", "CAREER_BASED", null, "Backend Developer");

        assertEquals(List.of("200"), node.getSuggestedCourseIds());
        assertEquals(List.of("11"), node.getSuggestedModuleIds());
    }

    @Test
    @DisplayName("matchNodesToCoursesAndModules should strip modules that do not belong to suggested courses")
    void matchNodesToCoursesAndModules_ShouldStripModulesOutsideSuggestedCourses() {
        CourseCatalogEntry course = catalog(400L, "Go Backend", "backend", "beginner", "go backend");

        when(catalogService.preSelectCourses(anyString(), anyInt(), any())).thenReturn(List.of(course));
        when(catalogService.getModuleIds(400L)).thenReturn(List.of(31L, 32L));
        when(catalogService.getModulePrerequisiteIds(400L, 31L)).thenReturn(List.of());
        when(catalogService.getModulePrerequisiteIds(400L, 32L)).thenReturn(List.of());

        when(moduleRepository.findModuleCoursePairsByIds(anyCollection())).thenReturn(List.<Object[]>of(
                new Object[]{31L, 400L},
                new Object[]{32L, 999L}
        ));

        RoadmapResponse.RoadmapNode node = node("n1", "Go backend path");

        matcher.matchNodesToCoursesAndModules(List.of(node), "Backend", "CAREER_BASED", null, "Backend Developer");

        assertEquals(List.of("400"), node.getSuggestedCourseIds());
        assertEquals(List.of("31"), node.getSuggestedModuleIds());
    }

    @Test
    @DisplayName("matchNodesToCoursesAndModules should keep all modules when cycle detected")
    void matchNodesToCoursesAndModules_ShouldKeepAllModulesWhenCycleDetected() {
        CourseCatalogEntry course = catalog(300L, "Python Backend", "backend", "beginner", "python backend");

        when(catalogService.preSelectCourses(anyString(), anyInt(), any())).thenReturn(List.of(course));
        when(catalogService.getModuleIds(300L)).thenReturn(List.of(21L, 22L, 23L));
        when(catalogService.getModulePrerequisiteIds(300L, 21L)).thenReturn(List.of(23L));
        when(catalogService.getModulePrerequisiteIds(300L, 22L)).thenReturn(List.of(21L));
        when(catalogService.getModulePrerequisiteIds(300L, 23L)).thenReturn(List.of(22L));

        when(moduleRepository.findModuleCoursePairsByIds(anyCollection())).thenReturn(List.<Object[]>of(
            new Object[]{21L, 300L},
            new Object[]{22L, 300L},
            new Object[]{23L, 300L}
        ));

        RoadmapResponse.RoadmapNode node = node("n1", "Python backend fundamentals");

        matcher.matchNodesToCoursesAndModules(List.of(node), "Backend", "CAREER_BASED", null, "Backend Developer");

        assertNotNull(node.getSuggestedModuleIds());
        assertEquals(3, node.getSuggestedModuleIds().size());
        assertTrue(Set.of("21", "22", "23").containsAll(node.getSuggestedModuleIds()));
    }

        @Test
        @DisplayName("matchNodesToCoursesAndModules should reject React drift for Java-intent node")
        void matchNodesToCoursesAndModules_ShouldRejectReactDriftForJavaIntentNode() {
        CourseCatalogEntry javaCourse = catalog(
            501L,
            "Java Backend Essentials",
            "backend",
            "beginner",
            "build backend services with java spring");
        CourseCatalogEntry reactCourse = catalog(
            502L,
            "React Frontend Bootcamp",
            "frontend",
            "beginner",
            "build reusable ui components");

        when(catalogService.preSelectCourses(anyString(), anyInt(), any())).thenReturn(List.of(reactCourse, javaCourse));
        when(catalogService.getModuleIds(501L)).thenReturn(List.of());

        RoadmapResponse.RoadmapNode node = node("n-java", "Java backend fundamentals");

        matcher.matchNodesToCoursesAndModules(
            List.of(node),
            "Java backend",
            "SKILL_BASED",
            "Java",
            null);

        assertEquals(List.of("501"), node.getSuggestedCourseIds());
        }

        @Test
        @DisplayName("matchNodesToCoursesAndModules should leave node unmatched when only non-anchored course exists")
        void matchNodesToCoursesAndModules_ShouldLeaveNodeUnmatchedWhenOnlyNonAnchoredCourseExists() {
        CourseCatalogEntry reactCourse = catalog(
            700L,
            "React Frontend Bootcamp",
            "frontend",
            "beginner",
            "build reusable ui components");

        when(catalogService.preSelectCourses(anyString(), anyInt(), any())).thenReturn(List.of(reactCourse));

        RoadmapResponse.RoadmapNode node = node("n-java", "Java backend foundations");

        matcher.matchNodesToCoursesAndModules(
            List.of(node),
            "Java backend",
            "SKILL_BASED",
            "Java",
            null);

        assertNull(node.getSuggestedCourseIds());
        }

    private CourseCatalogEntry catalog(Long id, String title, String category, String level, String description) {
        return CourseCatalogEntry.builder()
                .id(id)
                .title(title)
                .category(category)
                .level(level)
                .description(description)
                .shortDescription(description)
                .build();
    }

    private RoadmapResponse.RoadmapNode node(String id, String title) {
        return RoadmapResponse.RoadmapNode.builder()
                .id(id)
                .title(title)
                .difficulty("beginner")
                .keyConcepts(List.of("backend", "java"))
                .practicalExercises(List.of("build api"))
                .learningObjectives(List.of("master backend"))
                .build();
    }
}
