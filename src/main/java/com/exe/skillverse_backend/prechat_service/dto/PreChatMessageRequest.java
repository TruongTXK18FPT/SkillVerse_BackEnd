package com.exe.skillverse_backend.prechat_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreChatMessageRequest {
    @NotNull
    private Long mentorId;
    @Size(min = 1, max = 1000)
    private String content;
}

