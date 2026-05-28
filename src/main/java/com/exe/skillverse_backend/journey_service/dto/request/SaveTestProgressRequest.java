package com.exe.skillverse_backend.journey_service.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveTestProgressRequest {

    @NotNull(message = "Answers map is required")
    private Map<Long, Object> answers;

    private Integer timeSpentSeconds;
}
