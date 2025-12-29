package com.exe.skillverse_backend.parent_service.repository;

import com.exe.skillverse_backend.parent_service.entity.ParentStudentLink;
import com.exe.skillverse_backend.parent_service.entity.enums.LinkStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParentStudentLinkRepository extends JpaRepository<ParentStudentLink, Long> {
    
    List<ParentStudentLink> findByParentId(Long parentId);
    
    List<ParentStudentLink> findByStudentId(Long studentId);
    
    Optional<ParentStudentLink> findByParentIdAndStudentId(Long parentId, Long studentId);

    List<ParentStudentLink> findByParentIdAndStatus(Long parentId, LinkStatus status);
    
    boolean existsByParentIdAndStudentId(Long parentId, Long studentId);
}
