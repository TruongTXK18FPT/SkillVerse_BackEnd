package com.exe.skillverse_backend.ai_service.service;

import java.util.Set;

public interface TaxonomyService {

    void initExpertPacks();

    String mapToDomainPackId(String detectedDomainName);

    String normalizeToRoleId(String roleCategoryName);

    boolean isRoleKnown(String domainId, String roleId);

    Set<String> getKnownRolesForDomain(String domainId);

    Set<String> getAllowedTools(String domainId);

    Set<String> getAllowedSkills(String domainId, String roleId);

    boolean isSkillKnown(String domainId, String roleId, String skillName);

    boolean isToolKnown(String domainId, String toolName);

    String detectDomain(String target, String industry, String role);

    String detectRoleCategory(String roleOrTarget);

    String detectIndustry(String target, String provided);

    /**
     * Expand query terms with taxonomy-aware domain/role skills/tools.
     *
     * <p>Always returns a set containing original terms. Expansion is capped by
     * {@code maxExpansionTerms} and should gracefully degrade when taxonomy context
     * is unavailable.
     */
    Set<String> expandQueryWithTaxonomy(String rawQuery, Set<String> originalTerms, int maxExpansionTerms);
}
