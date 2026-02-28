package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ModuleRepository extends JpaRepository<Module, Long> {
  List<Module> findByCourseIdOrderByOrderIndexAsc(Long courseId);
  long countByCourseId(Long courseId);

  /**
   * Batch query to get module counts for multiple courses at once.
   * Eliminates N+1 query problem when listing courses.
   * Returns list of [courseId, moduleCount] pairs.
   */
  @Query("SELECT m.course.id, COUNT(m) FROM Module m WHERE m.course.id IN :courseIds GROUP BY m.course.id")
  List<Object[]> countByCourseIds(@Param("courseIds") Collection<Long> courseIds);
}


