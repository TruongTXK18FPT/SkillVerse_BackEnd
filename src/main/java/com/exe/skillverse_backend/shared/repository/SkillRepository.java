package com.exe.skillverse_backend.shared.repository;

import com.exe.skillverse_backend.shared.entity.Skill;
import com.exe.skillverse_backend.shared.enums.SkillStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SkillRepository extends JpaRepository<Skill, Long>, JpaSpecificationExecutor<Skill> {
    
   // Tìm theo tên
    Optional<Skill> findByNameIgnoreCase(String name);
    
    // Tìm theo canonical key (the source of truth for uniqueness)
    Optional<Skill> findByCanonicalKey(String canonicalKey);
    boolean existsByCanonicalKey(String canonicalKey);
    
    // Find active skills
    List<Skill> findByStatus(com.exe.skillverse_backend.shared.enums.SkillStatus status);

    @Query("""
       select s from Skill s
       where (:status is null or s.status = :status)
         and (
           :q is null
           or :q = ''
           or lower(s.name) like lower(concat('%', :q, '%'))
           or lower(coalesce(s.canonicalKey, '')) like lower(concat('%', :q, '%'))
           or lower(coalesce(s.description, '')) like lower(concat('%', :q, '%'))
         )
    """)
    Page<Skill> searchAdmin(
            @Param("q") String q,
            @Param("status") SkillStatus status,
            Pageable pageable);
    
    // Autocomplete — contains search (không chỉ prefix)
    Page<Skill> findByNameContainingIgnoreCase(String name, Pageable pageable);
    
    // Liệt kê con trực tiếp theo parentSkillId
    List<Skill> findByParentSkillIdOrderByNameAsc(Long parentSkillId);
    
    // Đếm số con trực tiếp
    long countByParentSkillId(Long parentSkillId);
    
    // Liệt kê root skills (parent null)
    Page<Skill> findByParentSkillIdIsNull(Pageable pageable);

    // ACTIVE-only variants for public APIs
    Page<Skill> findByParentSkillIdIsNullAndStatus(com.exe.skillverse_backend.shared.enums.SkillStatus status, Pageable pageable);
    List<Skill> findByParentSkillIdAndStatusOrderByNameAsc(Long parentSkillId, com.exe.skillverse_backend.shared.enums.SkillStatus status);
    Page<Skill> findByNameContainingIgnoreCaseAndStatus(String name, com.exe.skillverse_backend.shared.enums.SkillStatus status, Pageable pageable);
    
    // Tìm nhanh theo từ khóa (name/description) — chỉ ACTIVE
    @Query("""
       select s from Skill s
       where s.status = :status
         and (lower(s.name) like lower(concat('%', :q, '%'))
           or lower(s.description) like lower(concat('%', :q, '%')))
    """)
    Page<Skill> searchActive(@Param("q") String q, @Param("status") com.exe.skillverse_backend.shared.enums.SkillStatus status, Pageable pageable);

    // Tìm nhanh theo từ khóa (name/description) — legacy không filter status
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
    List<Skill> findByParentSkillId(Long parentSkillId);
    List<Skill> findByParentSkillIdIsNull(); // Root skills
    @Query("SELECT s FROM Skill s WHERE s.name LIKE %:name%")
    List<Skill> findByNameContaining(@Param("name") String name);
    @Query("SELECT s FROM Skill s WHERE s.description LIKE %:keyword%")
    List<Skill> findByDescriptionContaining(@Param("keyword") String keyword);
    boolean existsByName(String name);
}
