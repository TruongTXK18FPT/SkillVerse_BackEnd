package com.exe.skillverse_backend.ai_service.service.impl;

import com.exe.skillverse_backend.ai_service.service.TaxonomyService;
import com.exe.skillverse_backend.ai_service.service.dto.CourseCatalogEntry;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCourseCatalogServiceImplTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private ModuleRepository moduleRepository;

        @Mock
        private CourseEnrollmentRepository courseEnrollmentRepository;

    @Mock
    private TaxonomyService taxonomyService;

    @Mock
    private TfIdfVectorizer tfIdfVectorizer;

    private AiCourseCatalogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiCourseCatalogServiceImpl(
                courseRepository,
                moduleRepository,
                courseEnrollmentRepository,
                taxonomyService,
                tfIdfVectorizer);

        when(moduleRepository.countByCourseIds(anyCollection())).thenReturn(List.of());
        when(moduleRepository.findAllModulesWithCourseId(anyCollection())).thenReturn(List.of());
        when(moduleRepository.findPrerequisitesByCourseIds(anyCollection())).thenReturn(List.of());
    }

    @Test
    @DisplayName("preSelectCourses should use taxonomy expansion when base terms have no direct hit")
    void preSelectCourses_ShouldUseTaxonomyExpansion() {
        Instant now = Instant.parse("2026-04-10T10:15:30Z");
        when(courseRepository.findAllPublicCourseProjections()).thenReturn(List.<Object[]>of(
                row(1L, "Spring Boot API", "Build backend services", "", "Backend", "Beginner", now, 20L)
        ));

        when(taxonomyService.expandQueryWithTaxonomy(eq("microservice orchestration"), anySet(), anyInt()))
                .thenReturn(new LinkedHashSet<>(List.of("microservice", "orchestration", "spring")));
        when(tfIdfVectorizer.computeCosineScores(anyList(), anySet(), anyMap(), anyMap(), anyInt()))
                .thenReturn(Map.of());

        List<CourseCatalogEntry> results = service.preSelectCourses("microservice orchestration", 5);

        assertFalse(results.isEmpty());
        assertEquals(1L, results.get(0).getId());
        verify(taxonomyService).expandQueryWithTaxonomy(eq("microservice orchestration"), anySet(), anyInt());
    }

    @Test
    @DisplayName("preSelectCourses should boost newer and enrolled courses when BM25 ties")
    void preSelectCourses_ShouldApplyQualityWeighting() {
        Instant oldDate = Instant.parse("2024-01-10T10:15:30Z");
        Instant newDate = Instant.parse("2026-03-10T10:15:30Z");

        when(courseRepository.findAllPublicCourseProjections()).thenReturn(List.of(
                row(1L, "Backend Fundamentals", "backend roadmap", "", "Backend", "Beginner", oldDate, 2L),
                row(2L, "Backend Fundamentals", "backend roadmap", "", "Backend", "Beginner", newDate, 300L)
        ));

        when(taxonomyService.expandQueryWithTaxonomy(anyString(), anySet(), anyInt()))
                .thenAnswer(invocation -> new LinkedHashSet<>((Set<String>) invocation.getArgument(1)));
        when(tfIdfVectorizer.computeCosineScores(anyList(), anySet(), anyMap(), anyMap(), anyInt()))
                .thenReturn(Map.of());

        List<CourseCatalogEntry> results = service.preSelectCourses("backend", 2);

        assertEquals(2, results.size());
        assertEquals(2L, results.get(0).getId());
    }

    @Test
    @DisplayName("preSelectCourses should blend TF-IDF fallback when BM25 confidence is low")
    void preSelectCourses_ShouldUseTfIdfFallbackForLowBm25() {
        Instant sameDate = Instant.parse("2026-01-10T10:15:30Z");
        when(courseRepository.findAllPublicCourseProjections()).thenReturn(List.of(
                row(1L, "Backend for Java", "backend basics", "", "Backend", "Beginner", sameDate, 10L),
                row(2L, "Backend for Node", "backend basics", "", "Backend", "Beginner", sameDate, 10L)
        ));

        when(taxonomyService.expandQueryWithTaxonomy(anyString(), anySet(), anyInt()))
                .thenAnswer(invocation -> new LinkedHashSet<>((Set<String>) invocation.getArgument(1)));
        when(tfIdfVectorizer.computeCosineScores(anyList(), anySet(), anyMap(), anyMap(), anyInt()))
                .thenReturn(Map.of(1L, 0.1, 2L, 0.7));

        List<CourseCatalogEntry> results = service.preSelectCourses("backend", 2);

        assertEquals(2, results.size());
        assertEquals(2L, results.get(0).getId());
        verify(tfIdfVectorizer).computeCosineScores(anyList(), anySet(), anyMap(), anyMap(), anyInt());
    }

    @Test
    @DisplayName("preSelectCourses should down-rank completed courses for same user")
    void preSelectCourses_ShouldApplyUserHistoryPenalty() {
        Instant sameDate = Instant.parse("2026-01-10T10:15:30Z");
        when(courseRepository.findAllPublicCourseProjections()).thenReturn(List.of(
                row(1L, "Backend Fundamentals", "backend roadmap", "", "Backend", "Beginner", sameDate, 10L),
                row(2L, "Backend Fundamentals", "backend roadmap", "", "Backend", "Beginner", sameDate, 300L)
        ));

        when(taxonomyService.expandQueryWithTaxonomy(anyString(), anySet(), anyInt()))
                .thenAnswer(invocation -> new LinkedHashSet<>((Set<String>) invocation.getArgument(1)));
        when(tfIdfVectorizer.computeCosineScores(anyList(), anySet(), anyMap(), anyMap(), anyInt()))
                .thenReturn(Map.of());

        CourseEnrollment completedEnrollment = CourseEnrollment.builder()
                .course(Course.builder().id(2L).build())
                .status(EnrollmentStatus.COMPLETED)
                .progressPercent(100)
                .build();
        when(courseEnrollmentRepository.findByUserIdAndCourseIdIn(eq(99L), anyList()))
                .thenReturn(List.of(completedEnrollment));

        List<CourseCatalogEntry> results = service.preSelectCourses("backend", 2, 99L);

        assertEquals(2, results.size());
        assertEquals(1L, results.get(0).getId());
        verify(courseEnrollmentRepository).findByUserIdAndCourseIdIn(eq(99L), anyList());
    }

    private Object[] row(
            Long id,
            String title,
            String description,
            String shortDescription,
            String category,
            String level,
            Instant createdAt,
            Long enrollmentCount) {
        return new Object[]{
                id,
                title,
                description,
                shortDescription,
                category,
                level,
                createdAt,
                enrollmentCount
        };
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> anyMap() {
        return (Map<K, V>) org.mockito.ArgumentMatchers.any(Map.class);
    }
}
