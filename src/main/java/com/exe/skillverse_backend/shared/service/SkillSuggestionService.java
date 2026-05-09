package com.exe.skillverse_backend.shared.service;

import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.dto.SkillSuggestionDto;
import org.springframework.data.domain.Pageable;

public interface SkillSuggestionService {
    
    SkillSuggestionDto createSuggestion(SkillSuggestionDto dto, Long userId);
    
    PageResponse<SkillSuggestionDto> listPending(Pageable pageable);
    
    SkillSuggestionDto approve(Long id, Long adminId);
    
    SkillSuggestionDto merge(Long id, Long matchedSkillId, Long adminId);
    
    SkillSuggestionDto reject(Long id, String reviewNote, Long adminId);
}
