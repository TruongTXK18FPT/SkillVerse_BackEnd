package com.exe.skillverse_backend.seminar_service.validation;

import com.exe.skillverse_backend.seminar_service.dto.request.SeminarCreateRequest;
import com.exe.skillverse_backend.seminar_service.dto.request.SeminarUpdateRequest;
import com.exe.skillverse_backend.shared.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive validator for Seminar entities.
 * Provides detailed validation with Vietnamese error messages.
 */
@Component
public class SeminarValidator {

    /**
     * Validates seminar creation request.
     * 
     * @param request The seminar creation request
     * @throws ValidationException if validation fails
     */
    public void validateCreateRequest(SeminarCreateRequest request) {
        List<String> errors = new ArrayList<>();

        // Title validation
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            errors.add("Tiêu đề là bắt buộc");
        } else if (request.getTitle().length() > 200) {
            errors.add("Tiêu đề không được vượt quá 200 ký tự");
        }

        // Description validation
        if (request.getDescription() != null && request.getDescription().length() > 5000) {
            errors.add("Mô tả không được vượt quá 5000 ký tự");
        }

        // Meeting link validation
        if (request.getMeetingLink() == null || request.getMeetingLink().trim().isEmpty()) {
            errors.add("Link meeting là bắt buộc");
        } else if (!isValidMeetingLink(request.getMeetingLink())) {
            errors.add("Link meeting không hợp lệ (chỉ chấp nhận Google Meet, Zoom, Microsoft Teams)");
        }

        // DateTime validation
        validateDateTimes(request.getStartTime(), request.getEndTime(), errors);

        // Price validation
        if (request.getPrice() != null) {
            if (request.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                errors.add("Giá vé không được âm");
            }
            if (request.getPrice().compareTo(new BigDecimal("100000000")) > 0) {
                errors.add("Giá vé không được vượt quá 100,000,000 VNĐ");
            }
        }

        // Max capacity validation
        if (request.getMaxCapacity() != null) {
            if (request.getMaxCapacity() < 0) {
                errors.add("Số lượng vé tối đa không được âm");
            }
            if (request.getMaxCapacity() > 10000) {
                errors.add("Số lượng vé tối đa không được vượt quá 10,000");
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Dữ liệu không hợp lệ", errors);
        }
    }

    /**
     * Validates seminar update request.
     * 
     * @param request            The seminar update request
     * @param currentTicketsSold Current tickets sold count
     * @throws ValidationException if validation fails
     */
    public void validateUpdateRequest(SeminarUpdateRequest request, Integer currentTicketsSold) {
        List<String> errors = new ArrayList<>();

        // Title validation
        if (request.getTitle() != null) {
            if (request.getTitle().trim().isEmpty()) {
                errors.add("Tiêu đề không được để trống");
            } else if (request.getTitle().length() > 200) {
                errors.add("Tiêu đề không được vượt quá 200 ký tự");
            }
        }

        // Description validation
        if (request.getDescription() != null && request.getDescription().length() > 5000) {
            errors.add("Mô tả không được vượt quá 5000 ký tự");
        }

        // Meeting link validation
        if (request.getMeetingLink() != null) {
            if (request.getMeetingLink().trim().isEmpty()) {
                errors.add("Link meeting không được để trống");
            } else if (!isValidMeetingLink(request.getMeetingLink())) {
                errors.add("Link meeting không hợp lệ (chỉ chấp nhận Google Meet, Zoom, Microsoft Teams)");
            }
        }

        // DateTime validation
        if (request.getStartTime() != null || request.getEndTime() != null) {
            validateDateTimes(request.getStartTime(), request.getEndTime(), errors);
        }

        // Price validation
        if (request.getPrice() != null) {
            if (request.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                errors.add("Giá vé không được âm");
            }
            if (request.getPrice().compareTo(new BigDecimal("100000000")) > 0) {
                errors.add("Giá vé không được vượt quá 100,000,000 VNĐ");
            }
        }

        // Max capacity validation with current tickets sold
        if (request.getMaxCapacity() != null) {
            if (request.getMaxCapacity() < 0) {
                errors.add("Số lượng vé tối đa không được âm");
            }
            if (request.getMaxCapacity() > 10000) {
                errors.add("Số lượng vé tối đa không được vượt quá 10,000");
            }
            if (currentTicketsSold != null && request.getMaxCapacity() > 0
                    && request.getMaxCapacity() < currentTicketsSold) {
                errors.add(String.format(
                        "Không thể đặt số lượng vé tối đa (%d) thấp hơn số vé đã bán (%d)",
                        request.getMaxCapacity(),
                        currentTicketsSold));
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Dữ liệu cập nhật không hợp lệ", errors);
        }
    }

    /**
     * Validates start and end date/time.
     * 
     * @param startTime Start time
     * @param endTime   End time
     * @param errors    List to collect errors
     */
    private void validateDateTimes(LocalDateTime startTime, LocalDateTime endTime, List<String> errors) {
        LocalDateTime now = LocalDateTime.now();

        // Start time validation
        if (startTime == null) {
            errors.add("Thời gian bắt đầu là bắt buộc");
        } else {
            if (startTime.isBefore(now)) {
                errors.add("Thời gian bắt đầu phải sau thời điểm hiện tại");
            }
            if (startTime.getYear() > now.getYear() + 5) {
                errors.add("Thời gian bắt đầu không được quá xa trong tương lai (tối đa 5 năm)");
            }
        }

        // End time validation
        if (endTime == null) {
            errors.add("Thời gian kết thúc là bắt buộc");
        } else {
            if (endTime.isBefore(now)) {
                errors.add("Thời gian kết thúc phải sau thời điểm hiện tại");
            }
            if (endTime.getYear() > now.getYear() + 5) {
                errors.add("Thời gian kết thúc không được quá xa trong tương lai (tối đa 5 năm)");
            }
        }

        // Compare start and end time
        if (startTime != null && endTime != null) {
            if (endTime.isBefore(startTime) || endTime.isEqual(startTime)) {
                errors.add("Thời gian kết thúc phải sau thời gian bắt đầu");
            }

            // Check minimum duration (at least 30 minutes)
            long minutesBetween = Duration.between(startTime, endTime).toMinutes();
            if (minutesBetween < 30) {
                errors.add("Hội thảo phải kéo dài ít nhất 30 phút");
            }

            // Check maximum duration (not more than 8 hours)
            if (minutesBetween > 480) {
                errors.add("Hội thảo không nên kéo dài quá 8 giờ");
            }

            // Check if start and end are on same day (recommended)
            if (!startTime.toLocalDate().equals(endTime.toLocalDate())) {
                // This is a warning, not an error - could be allowed
                // errors.add("Khuyến nghị: Hội thảo nên diễn ra trong cùng một ngày");
            }
        }
    }

    /**
     * Validates meeting link format.
     * Accepts Google Meet, Zoom, Microsoft Teams links.
     * Does NOT require http/https prefix - user can input any format.
     */
    private boolean isValidMeetingLink(String link) {
        if (link == null || link.trim().isEmpty()) {
            return false;
        }

        String lowerLink = link.toLowerCase().trim();

        // Accept Google Meet, Zoom, Microsoft Teams
        // No http/https requirement - just check domain presence
        return lowerLink.contains("meet.google.com")
                || lowerLink.contains("zoom.us")
                || lowerLink.contains("teams.microsoft.com")
                || lowerLink.contains("teams.live.com");
    }

    /**
     * Validates that a seminar can be submitted for approval.
     */
    public void validateSubmission(LocalDateTime startTime) {
        List<String> errors = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();
        if (startTime.isBefore(now)) {
            errors.add("Không thể gửi duyệt hội thảo đã qua thời gian bắt đầu");
        }

        // Check if start time is too soon (at least 24 hours in advance)
        long hoursUntilStart = Duration.between(now, startTime).toHours();
        if (hoursUntilStart < 24) {
            errors.add("Hội thảo phải được gửi duyệt trước ít nhất 24 giờ so với thời gian bắt đầu");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Không thể gửi duyệt hội thảo", errors);
        }
    }
}
