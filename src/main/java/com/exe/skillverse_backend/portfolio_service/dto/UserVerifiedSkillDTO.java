package com.exe.skillverse_backend.portfolio_service.dto;

import com.exe.skillverse_backend.portfolio_service.entity.UserVerifiedSkill;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserVerifiedSkillDTO {
    private Long id;
    private String skillName;
    private String skillLevel;
    private Long verifiedByMentorId;
    private String verifiedByMentorName;  // enriched at service layer
    private Long journeyId;
    private Long bookingId;
    private String verificationNote;
    private Integer featuredOrder;
    private Instant verifiedAt;

    public static UserVerifiedSkillDTO from(UserVerifiedSkill entity) {
        return UserVerifiedSkillDTO.builder()
                .id(entity.getId())
                .skillName(entity.getSkillName())
                .skillLevel(entity.getSkillLevel())
                .verifiedByMentorId(entity.getVerifiedByMentorId())
                .journeyId(entity.getJourneyId())
                .bookingId(entity.getBookingId())
                .verificationNote(entity.getVerificationNote())
                .featuredOrder(entity.getFeaturedOrder())
                .verifiedAt(entity.getVerifiedAt())
                .build();
    }
}
