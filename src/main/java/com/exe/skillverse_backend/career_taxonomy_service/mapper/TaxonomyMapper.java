package com.exe.skillverse_backend.career_taxonomy_service.mapper;

import com.exe.skillverse_backend.career_taxonomy_service.dto.DomainDto;
import com.exe.skillverse_backend.career_taxonomy_service.dto.JobPositionDto;
import com.exe.skillverse_backend.career_taxonomy_service.dto.JobPositionTrackDto;
import com.exe.skillverse_backend.career_taxonomy_service.dto.JobPositionTrackSkillDto;
import com.exe.skillverse_backend.career_taxonomy_service.entity.Domain;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPosition;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPositionTrack;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPositionTrackSkill;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = CustomMapperConfig.class)
public interface TaxonomyMapper {

    DomainDto toDomainDto(Domain e);
    Domain toDomain(DomainDto d);
    List<DomainDto> toDomainDtos(List<Domain> list);

    JobPositionDto toJobPositionDto(JobPosition e);
    JobPosition toJobPosition(JobPositionDto d);
    List<JobPositionDto> toJobPositionDtos(List<JobPosition> list);

    JobPositionTrackDto toJobPositionTrackDto(JobPositionTrack e);
    JobPositionTrack toJobPositionTrack(JobPositionTrackDto d);
    List<JobPositionTrackDto> toJobPositionTrackDtos(List<JobPositionTrack> list);

    @Mapping(source = "skill.name", target = "skillName")
    @Mapping(source = "skill.canonicalKey", target = "canonicalKey")
    JobPositionTrackSkillDto toTrackSkillDto(JobPositionTrackSkill e);

    @Mapping(target = "skill", ignore = true)
    @Mapping(target = "track", ignore = true)
    JobPositionTrackSkill toTrackSkill(JobPositionTrackSkillDto d);
    List<JobPositionTrackSkillDto> toTrackSkillDtos(List<JobPositionTrackSkill> list);
}
