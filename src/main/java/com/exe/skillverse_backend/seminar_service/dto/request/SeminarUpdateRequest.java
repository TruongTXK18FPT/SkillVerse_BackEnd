package com.exe.skillverse_backend.seminar_service.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SeminarUpdateRequest {
    @Size(min = 1, max = 200, message = "Tiêu đề phải từ 1-200 ký tự")
    private String title;

    @Size(max = 5000, message = "Mô tả không được vượt quá 5000 ký tự")
    private String description;
    private String imageUrl;
    private String meetingLink;

    @Future(message = "Thời gian bắt đầu phải trong tương lai")
    private LocalDateTime startTime;

    @Future(message = "Thời gian kết thúc phải trong tương lai")
    private LocalDateTime endTime;

    @PositiveOrZero(message = "Giá vé không được âm")
    @DecimalMax(value = "100000000", message = "Giá vé không được vượt quá 100,000,000 VND")
    private BigDecimal price;

    /**
     * Maximum number of tickets that can be sold.
     * NULL or 0 means unlimited capacity.
     */
    @Min(value = 0, message = "Số lượng vé tối đa phải >= 0 (0 = không giới hạn)")
    @Max(value = 10000, message = "Số lượng vé tối đa không được vượt quá 10,000")
    private Integer maxCapacity;
}
