package com.exe.skillverse_backend.shared.service;

/**
 * Contract for checking if a Skill is currently referenced by any downstream entity
 * (e.g., JobPositionTrackSkill). Implemented in career_taxonomy_service to avoid
 * a reverse dependency from shared -> career_taxonomy_service.
 */
public interface SkillReferenceGuard {

    /**
     * Returns true if the given skillId is actively referenced by at least one
     * downstream record (e.g., mapped in a JobPositionTrack).
     *
     * @param skillId the id of the Skill to check
     * @return true if referenced, false otherwise
     */
    boolean isSkillReferenced(Long skillId);
}
