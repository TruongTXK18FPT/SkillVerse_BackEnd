package com.exe.skillverse_backend.shared.repository;

import com.exe.skillverse_backend.shared.entity.Skill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillRepository extends JpaRepository<Skill, Long>, JpaSpecificationExecutor<Skill> {
    
    // Tìm theo tên (unique trong 1 category nếu muốn enforce ở service)
    Optional<Skill> findByNameIgnoreCase(String name);
    
    // Tìm theo tên + category (phục vụ enforce unique "name+category")
    Optional<Skill> findByNameIgnoreCaseAndCategoryIgnoreCase(String name, String category);
    
    // Autocomplete theo prefix
    Page<Skill> findByNameStartingWithIgnoreCase(String prefix, Pageable pageable);
    
    // Tìm theo category
    Page<Skill> findByCategoryIgnoreCase(String category, Pageable pageable);
    
    // Liệt kê con trực tiếp theo parentSkillId
    List<Skill> findByParentSkillIdOrderByNameAsc(Long parentSkillId);
    
    // Đếm số con trực tiếp
    long countByParentSkillId(Long parentSkillId);
    
    // Liệt kê root skills (parent null)
    Page<Skill> findByParentSkillIdIsNull(Pageable pageable);
    
    // Tìm nhanh theo từ khóa (name/description)
    @Query("""
       select s from Skill s
       where lower(s.name) like lower(concat('%', :q, '%'))
          or lower(s.description) like lower(concat('%', :q, '%'))
    """)
    Page<Skill> search(@Param("q") String q, Pageable pageable);
    
    // Kiểm tra tồn tại theo id
    boolean existsById(Long id);

    // Legacy methods - keeping for compatibility
    Optional<Skill> findByName(String name);
    List<Skill> findByCategory(String category);
    List<Skill> findByParentSkillId(Long parentSkillId);
    List<Skill> findByParentSkillIdIsNull(); // Root skills
    @Query("SELECT s FROM Skill s WHERE s.name LIKE %:name%")
    List<Skill> findByNameContaining(@Param("name") String name);
    @Query("SELECT s FROM Skill s WHERE s.description LIKE %:keyword%")
    List<Skill> findByDescriptionContaining(@Param("keyword") String keyword);
    boolean existsByName(String name);
}