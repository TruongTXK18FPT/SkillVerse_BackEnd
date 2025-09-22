package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.CourseSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public interface CourseSkillRepository extends JpaRepository<CourseSkill, Serializable>, JpaSpecificationExecutor<CourseSkill> {

    /**
     * Get skill names associated with a course
     */
    @Transactional(readOnly = true)
    @Query("SELECT s.name FROM CourseSkill cs JOIN cs.skill s WHERE cs.course.id = :courseId")
    List<String> listSkillNames(@Param("courseId") Long courseId);

    /**
     * Delete all course-skill associations by course ID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM CourseSkill cs WHERE cs.course.id = :courseId")
    int deleteByCourseId(@Param("courseId") Long courseId);

    /**
     * Find course-skill associations by course ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT cs FROM CourseSkill cs WHERE cs.course.id = :courseId")
    List<CourseSkill> findByCourseId(@Param("courseId") Long courseId);

    /**
     * Find course-skill associations by skill ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT cs FROM CourseSkill cs WHERE cs.skill.id = :skillId")
    List<CourseSkill> findBySkillId(@Param("skillId") Long skillId);

    /**
     * Find specific course-skill association
     */
    @Transactional(readOnly = true)
    @Query("SELECT cs FROM CourseSkill cs WHERE cs.course.id = :courseId AND cs.skill.id = :skillId")
    Optional<CourseSkill> findByCourseIdAndSkillId(@Param("courseId") Long courseId, @Param("skillId") Long skillId);

    /**
     * Check if course has specific skill
     */
    @Transactional(readOnly = true)
    @Query("SELECT CASE WHEN COUNT(cs) > 0 THEN true ELSE false END " +
           "FROM CourseSkill cs WHERE cs.course.id = :courseId AND cs.skill.id = :skillId")
    boolean existsByCourseIdAndSkillId(@Param("courseId") Long courseId, @Param("skillId") Long skillId);

    /**
     * Count skills for a course
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(cs) FROM CourseSkill cs WHERE cs.course.id = :courseId")
    long countByCourseId(@Param("courseId") Long courseId);

    /**
     * Find courses by skill name
     */
    @Transactional(readOnly = true)
    @Query("SELECT cs.course.id FROM CourseSkill cs WHERE cs.skill.name = :skillName")
    List<Long> findCourseIdsBySkillName(@Param("skillName") String skillName);

    /**
     * Delete specific course-skill association
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM CourseSkill cs WHERE cs.course.id = :courseId AND cs.skill.id = :skillId")
    int deleteByCourseIdAndSkillId(@Param("courseId") Long courseId, @Param("skillId") Long skillId);

    /**
     * Get skill objects associated with a course
     */
    @Transactional(readOnly = true)
    @Query("SELECT cs.skill FROM CourseSkill cs WHERE cs.course.id = :courseId")
    List<com.exe.skillverse_backend.shared.entity.Skill> findSkillsByCourseId(@Param("courseId") Long courseId);
}
