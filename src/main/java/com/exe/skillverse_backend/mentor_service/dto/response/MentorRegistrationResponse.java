package com.exe.skillverse_backend.mentor_service.dto.response;

import com.exe.skillverse_backend.shared.dto.response.BaseRegistrationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Mentor registration response")
public class MentorRegistrationResponse extends BaseRegistrationResponse {

    @Schema(description = "Mentor profile ID", example = "456")
    private Long mentorProfileId;

    @Schema(description = "Application status", example = "PENDING")
    private String applicationStatus;

    @Schema(description = "Role assigned", example = "MENTOR")
    private String role;
}