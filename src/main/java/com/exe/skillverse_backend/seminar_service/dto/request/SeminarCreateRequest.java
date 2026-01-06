package com.exe.skillverse_backend.seminar_service.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SeminarCreateRequest {
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Tiêu đề không được vượt quá 200 ký tự")
    private String title;

    @Size(max = 5000, message = "Mô tả không được vượt quá 5000 ký tự")
    private String description;
    private String imageUrl;

    @NotBlank(message = "Meeting link is required")
    private String meetingLink;

    @NotNull(message = "Start time is required")
    @Future(message = "Start time must be in the future")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    @Future(message = "End time must be in the future")
    private LocalDateTime endTime;

    @PositiveOrZero(message = "Giá vé không được âm")
    @DecimalMax(value = "100000000", message = "Giá vé không được vượt quá 100,000,000 VND")
    private BigDecimal price; // Can be null (default 0)

    /**
     * Maximum number of tickets that can be sold.
     * NULL or 0 means unlimited capacity.
     */
    @Min(value = 0, message = "Số lượng vé tối đa phải >= 0 (0 = không giới hạn)")
    @Max(value = 10000, message = "Số lượng vé tối đa không được vượt quá 10,000")
    private Integer maxCapacity;
}
