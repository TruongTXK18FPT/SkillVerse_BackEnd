package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.Module;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ModuleRepository extends JpaRepository<Module, Long> {
  List<Module> findByCourseIdOrderByOrderIndexAsc(Long courseId);
  long countByCourseId(Long courseId);

  /**
   * Fetch modules by course id, ordered by orderIndex.
   * Child collections (lessons, quizzes, assignments) are loaded via batch fetch
   * (configured in Hibernate config), avoiding N+1 with a fixed small number of queries
   * instead of one-per-child.
   *
   * Previously used @EntityGraph with multiple attributePaths which triggered
   * MultipleBagFetchException (Hibernate 6 cannot fetch 2+ List collections in a single
   * Cartesian JOIN). Solution: fetch modules only, let batch fetch handle children.
   */
  @Query("SELECT DISTINCT m FROM Module m WHERE m.course.id = :courseId ORDER BY m.orderIndex ASC")
  List<Module> findByCourseIdWithContent(@Param("courseId") Long courseId);

  /**
   * Batch query to get module counts for multiple courses at once.
   * Eliminates N+1 query problem when listing courses.
   * Returns list of [courseId, moduleCount] pairs.
   */
  @Query("SELECT m.course.id, COUNT(m) FROM Module m WHERE m.course.id IN :courseIds GROUP BY m.course.id")
  List<Object[]> countByCourseIds(@Param("courseIds") Collection<Long> courseIds);

  /**
   * Lightweight projection of all modules for given courses, ordered by orderIndex.
   * Returns only [courseId, moduleId, title, orderIndex] — no nested content.
   *
   * <p>Used by {@link com.exe.skillverse_backend.ai_service.service.impl.AiCourseCatalogServiceImpl}
   * to build the in-memory course catalog index (Phase A pre-selection).
   */
  @Transactional(readOnly = true)
  @Query("""
      SELECT m.course.id, m.id, m.title, m.orderIndex
      FROM Module m
      WHERE m.course.id IN :courseIds
      ORDER BY m.course.id, m.orderIndex ASC
      """)
  List<Object[]> findAllModulesWithCourseId(@Param("courseIds") Collection<Long> courseIds);

  /**
   * Fetch prerequisite module IDs for all modules belonging to given courses.
   * Returns [moduleId, prerequisiteModuleId] pairs.
   *
   * <p>Used by {@link com.exe.skillverse_backend.ai_service.service.impl.MultiLevelCourseMatcher}
   * to build the prerequisite graph for topological-sort module distribution.
   */
  @Transactional(readOnly = true)
  @Query("""
      SELECT m.id, p FROM Module m JOIN m.prerequisiteModuleIds p
      WHERE m.course.id IN :courseIds
      """)
  List<Object[]> findPrerequisitesByCourseIds(@Param("courseIds") Collection<Long> courseIds);

  /**
   * Fetch module-course relationships for a batch of module IDs.
   * Returns [moduleId, courseId] pairs for existing modules only.
   */
  @Transactional(readOnly = true)
  @Query("""
      SELECT m.id, m.course.id
      FROM Module m
      WHERE m.id IN :moduleIds
      """)
  List<Object[]> findModuleCoursePairsByIds(@Param("moduleIds") Collection<Long> moduleIds);

  /**
   * Fetch modules by ID with lessons eagerly loaded.
   * Returns modules sorted by orderIndex.
   *
   * <p>Used by {@link com.exe.skillverse_backend.journey_service.service.impl.JourneyServiceImpl}
   * to build course module context for AI Study Planner when generating study plans
   * from roadmap nodes with suggestedModuleIds.
   */
  @Query("SELECT m FROM Module m LEFT JOIN FETCH m.lessons WHERE m.id IN :ids ORDER BY m.orderIndex ASC")
  List<Module> findByIdInWithLessons(@Param("ids") List<Long> ids);
}


