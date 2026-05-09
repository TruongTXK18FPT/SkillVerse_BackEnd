package com.exe.skillverse_backend.shared.mapper;

import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import com.exe.skillverse_backend.shared.dto.SkillSuggestionDto;
import com.exe.skillverse_backend.shared.entity.SkillSuggestion;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(config = CustomMapperConfig.class)
public interface SkillSuggestionMapper {
    
    SkillSuggestionDto toDto(SkillSuggestion entity);
    
    SkillSuggestion toEntity(SkillSuggestionDto dto);
    
    List<SkillSuggestionDto> toDtos(List<SkillSuggestion> entities);
}
