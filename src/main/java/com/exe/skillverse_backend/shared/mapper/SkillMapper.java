package com.exe.skillverse_backend.shared.mapper;

import com.exe.skillverse_backend.shared.dto.SkillDto;
import com.exe.skillverse_backend.shared.entity.Skill;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = com.exe.skillverse_backend.shared.config.CustomMapperConfig.class)
public interface SkillMapper {
    
    SkillDto toDto(Skill e);
    
    Skill toEntity(SkillDto d);
    
    void updateEntity(SkillDto d, @MappingTarget Skill e);
    
    List<SkillDto> toDtos(List<Skill> list);
}