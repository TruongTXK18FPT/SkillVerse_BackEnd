package com.exe.skillverse_backend.shared.service;

import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.dto.SkillDto;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface SkillService {
    
    SkillDto create(SkillDto dto);                          // tạo mới (có thể truyền parentSkillId)
    
    SkillDto createApproved(SkillDto dto, Long approvedBy); // tạo mới khi admin approve suggestion
    
    SkillDto resolve(String rawSkillName);                  // resolve a skill by its raw name (canonical check)
    
    List<SkillDto> listActive();
    
    // Retrieves all skills including INACTIVE
    List<SkillDto> listAll();                            // list all active skills

    SkillDto update(Long id, SkillDto dto);                 // cập nhật name/description/parentSkillId
    
    void delete(Long id);                                   // soft delete (deactivate)
    
    void reactivate(Long id);                               // reactivate skill
    
    void hardDelete(Long id);                               // hard delete
    
    SkillDto get(Long id);                                  // chi tiết skill
    
    PageResponse<SkillDto> search(String q, Pageable p);    // tìm theo name/description
    
    PageResponse<SkillDto> listRoots(Pageable p);           // parentSkillId == null
    
    List<SkillDto> listChildren(Long parentId);             // con trực tiếp
    
    SkillDto reparent(Long id, Long newParentId);           // đổi cha (chống cycle)
    
    List<Long> pathToRoot(Long id);                         // đường đi từ node -> root (ids)
    
    PageResponse<SkillDto> suggestByPrefix(String prefix, Pageable p); // autocomplete
}