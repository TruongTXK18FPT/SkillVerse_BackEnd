package com.exe.skillverse_backend.mentor_booking_service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingRequest {
    @Min(1)
    @Max(5)
    private Integer stars;
    @Size(max = 1000)
    private String comment;
    private String skillEndorsed;
}

