package com.exe.skillverse_backend.portfolio_service.service.impl;

import com.exe.skillverse_backend.portfolio_service.dto.CandidateSummaryDTO;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.portfolio_service.service.RecruiterCandidateService;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.service.UsageLimitService;
import com.exe.skillverse_backend.premium_service.dto.response.UsageCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecruiterCandidateServiceImpl implements RecruiterCandidateService {

    private final PortfolioExtendedProfileRepository portfolioRepository;
    private final UsageLimitService usageLimitService;

    @Override
    @Transactional(readOnly = true)
    public Page<CandidateSummaryDTO> getOpenCandidates(Pageable pageable) {
        log.info("Fetching open candidates for recruiter view");

        // Debug info
        long total = portfolioRepository.count();
        log.info("Total portfolios in DB: {}", total);

        Page<PortfolioExtendedProfile> profiles = portfolioRepository.findPortfoliosOpenToOffers(pageable);
        log.info("Found {} open profiles matching criteria", profiles.getTotalElements());

        if (profiles.isEmpty() && total > 0) {
            log.warn("No open profiles found despite having {} total profiles. Checking data...", total);
            portfolioRepository.findAll().stream().limit(10)
                    .forEach(p -> log.info("User {}: isPublic={}, allowJobOffers={}",
                            p.getUserId(), p.getIsPublic(), p.getAllowJobOffers()));
        }

        return profiles.map(this::mapToDTO);
    }

    private CandidateSummaryDTO mapToDTO(PortfolioExtendedProfile profile) {
        boolean isHighlighted = false;
        try {
            UsageCheckResult result = usageLimitService.canUseFeature(profile.getUserId(),
                    FeatureType.PRIORITY_SUPPORT);
            isHighlighted = Boolean.TRUE.equals(result.getAllowed());
        } catch (Exception e) {
            log.warn("Failed to check premium status for user {}: {}", profile.getUserId(), e.getMessage());
            // Default to false on error to avoid breaking the list
        }

        return CandidateSummaryDTO.builder()
                .userId(profile.getUserId())
                .fullName(profile.getFullName())
                .professionalTitle(profile.getProfessionalTitle())
                .avatarUrl(profile.getAvatarUrl())
                .customUrlSlug(profile.getCustomUrlSlug())
                .topSkills(profile.getTopSkills())
                .isHighlighted(isHighlighted)
                .hourlyRate(profile.getHourlyRate())
                .preferredCurrency(profile.getPreferredCurrency())
                .totalProjects(profile.getTotalProjects())
                .build();
    }
}
