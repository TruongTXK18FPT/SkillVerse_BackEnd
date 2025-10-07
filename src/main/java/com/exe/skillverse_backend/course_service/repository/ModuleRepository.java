package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ModuleRepository extends JpaRepository<Module, Long> {
  List<Module> findByCourseIdOrderByOrderIndexAsc(Long courseId);
  long countByCourseId(Long courseId);
}


