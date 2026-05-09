package com.exe.skillverse_backend.career_taxonomy_service.service.impl;

import com.exe.skillverse_backend.career_taxonomy_service.repository.JobPositionTrackSkillRepository;
import com.exe.skillverse_backend.shared.service.SkillReferenceGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Implements SkillReferenceGuard by checking JobPositionTrackSkill mappings.
 * Lives in career_taxonomy_service so the dependency direction is correct:
 *   career_taxonomy_service -> shared  (correct)
 *   shared -> career_taxonomy_service  (forbidden, now avoided)
 */
@Service
@RequiredArgsConstructor
public class TaxonomySkillReferenceGuard implements SkillReferenceGuard {

    private final JobPositionTrackSkillRepository trackSkillRepository;

    @Override
    public boolean isSkillReferenced(Long skillId) {
        return trackSkillRepository.existsBySkillId(skillId);
    }
}
