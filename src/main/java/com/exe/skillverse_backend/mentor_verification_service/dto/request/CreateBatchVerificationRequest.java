package com.exe.skillverse_backend.mentor_verification_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBatchVerificationRequest {

    @NotEmpty(message = "At least one skill is required")
    @Size(max = 50, message = "A batch can contain at most 50 skills")
    @Builder.Default
    private List<@Size(max = 100, message = "Skill name must be at most 100 characters") String> skillNames = new ArrayList<>();

    @Size(max = 500, message = "GitHub URL must be at most 500 characters")
    private String githubUrl;

    @Size(max = 500, message = "Portfolio URL must be at most 500 characters")
    private String portfolioUrl;

    @Size(max = 2000, message = "Additional notes must be at most 2000 characters")
    private String additionalNotes;

    @Builder.Default
    private List<Long> certificateIds = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<CreateMentorVerificationRequest.EvidenceItem> evidences = new ArrayList<>();
}
