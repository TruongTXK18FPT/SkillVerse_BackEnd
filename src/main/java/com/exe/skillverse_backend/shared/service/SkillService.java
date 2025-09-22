package com.exe.skillverse_backend.shared.service;

import com.exe.skillverse_backend.shared.dto.SkillDto;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SkillService {
    
    SkillDto create(SkillDto dto);                          // tạo mới (có thể truyền parentSkillId)
    
    SkillDto update(Long id, SkillDto dto);                 // cập nhật name/category/description/parentSkillId
    
    void delete(Long id);                                   // xóa; nếu có con => chặn hoặc chuyển orphan (tùy policy)
    
    SkillDto get(Long id);                                  // chi tiết skill
    
    PageResponse<SkillDto> search(String q, Pageable p);    // tìm theo name/description
    
    PageResponse<SkillDto> listByCategory(String category, Pageable p);
    
    PageResponse<SkillDto> listRoots(Pageable p);           // parentSkillId == null
    
    List<SkillDto> listChildren(Long parentId);             // con trực tiếp
    
    SkillDto reparent(Long id, Long newParentId);           // đổi cha (chống cycle)
    
    List<Long> pathToRoot(Long id);                         // đường đi từ node -> root (ids)
    
    PageResponse<SkillDto> suggestByPrefix(String prefix, Pageable p); // autocomplete
}