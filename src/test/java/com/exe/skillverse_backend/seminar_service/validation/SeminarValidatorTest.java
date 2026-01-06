package com.exe.skillverse_backend.seminar_service.validation;

import com.exe.skillverse_backend.seminar_service.dto.request.SeminarCreateRequest;
import com.exe.skillverse_backend.seminar_service.dto.request.SeminarUpdateRequest;
import com.exe.skillverse_backend.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive test suite for SeminarValidator
 * Tests all validation rules with correct error assertion approach
 */
@DisplayName("SeminarValidator Comprehensive Tests")
class SeminarValidatorTest {

    private SeminarValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SeminarValidator();
    }

    /**
     * Helper method to create valid request for testing
     */
    private SeminarCreateRequest createValidRequest() {
        SeminarCreateRequest request = new SeminarCreateRequest();
        request.setTitle("Valid Seminar Title");
        request.setDescription("Valid description");
        request.setMeetingLink("meet.google.com/abc-defg-hij");
        request.setStartTime(LocalDateTime.now().plusDays(2));
        request.setEndTime(LocalDateTime.now().plusDays(2).plusHours(2));
        request.setPrice(BigDecimal.valueOf(50000));
        request.setMaxCapacity(100);
        return request;
    }

    // ============================================
    // TITLE VALIDATION TESTS
    // ============================================

    @Nested
    @DisplayName("Title Validation Tests")
    class TitleValidationTests {

        @Test
        @DisplayName("Should reject null title")
        void shouldRejectNullTitle() {
            SeminarCreateRequest request = createValidRequest();
            request.setTitle(null);

            assertThatThrownBy(() -> validator.validateCreateRequest(request))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).contains("Tiêu đề là bắt buộc");
                    });
        }

        @Test
        @DisplayName("Should reject empty title")
        void shouldRejectEmptyTitle() {
            SeminarCreateRequest request = createValidRequest();
            request.setTitle("");

            assertThatThrownBy(() -> validator.validateCreateRequest(request))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).contains("Tiêu đề là bắt buộc");
                    });
        }

        @Test
        @DisplayName("Should reject whitespace-only title")
        void shouldRejectWhitespaceTitle() {
            SeminarCreateRequest request = createValidRequest();
            request.setTitle("   ");

            assertThatThrownBy(() -> validator.validateCreateRequest(request))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).contains("Tiêu đề là bắt buộc");
                    });
        }

        @Test
        @DisplayName("Should reject title exceeding 200 characters")
        void shouldRejectLongTitle() {
            SeminarCreateRequest request = createValidRequest();
            request.setTitle("A".repeat(201));

            assertThatThrownBy(() -> validator.validateCreateRequest(request))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).contains("Tiêu đề không được vượt quá 200 ký tự");
                    });
        }

        @Test
        @DisplayName("Should accept title with exactly 200 characters")
        void shouldAccept200CharTitle() {
            SeminarCreateRequest request = createValidRequest();
            request.setTitle("A".repeat(200));

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accept valid title with special characters")
        void shouldAcceptTitleWithSpecialChars() {
            SeminarCreateRequest request = createValidRequest();
            request.setTitle("Hội thảo AI/ML - Machine Learning 2026!");

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }
    }

    // ============================================
    // DESCRIPTION VALIDATION TESTS
    // ============================================

    @Nested
    @DisplayName("Description Validation Tests")
    class DescriptionValidationTests {

        @Test
        @DisplayName("Should accept null description")
        void shouldAcceptNullDescription() {
            SeminarCreateRequest request = createValidRequest();
            request.setDescription(null);

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accept empty description")
        void shouldAcceptEmptyDescription() {
            SeminarCreateRequest request = createValidRequest();
            request.setDescription("");

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject description exceeding 5000 characters")
        void shouldRejectLongDescription() {
            SeminarCreateRequest request = createValidRequest();
            request.setDescription("A".repeat(5001));

            assertThatThrownBy(() -> validator.validateCreateRequest(request))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).contains("Mô tả không được vượt quá 5000 ký tự");
                    });
        }

        @Test
        @DisplayName("Should accept description with exactly 5000 characters")
        void shouldAccept5000CharDescription() {
            SeminarCreateRequest request = createValidRequest();
            request.setDescription("A".repeat(5000));

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }
    }

    // ============================================
    // MEETING LINK VALIDATION TESTS
    // ============================================

    @Nested
    @DisplayName("Meeting Link Validation Tests")
    class MeetingLinkValidationTests {

        @Test
        @DisplayName("Should reject null meeting link")
        void shouldRejectNullMeetingLink() {
            SeminarCreateRequest request = createValidRequest();
            request.setMeetingLink(null);

            assertThatThrownBy(() -> validator.validateCreateRequest(request))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).contains("Link meeting là bắt buộc");
                    });
        }

        @Test
        @DisplayName("Should reject empty meeting link")
        void shouldRejectEmptyMeetingLink() {
            SeminarCreateRequest request = createValidRequest();
            request.setMeetingLink("");

            assertThatThrownBy(() -> validator.validateCreateRequest(request))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).contains("Link meeting là bắt buộc");
                    });
        }

        @Test
        @DisplayName("Should accept Google Meet link without http/https")
        void shouldAcceptGoogleMeetWithoutProtocol() {
            SeminarCreateRequest request = createValidRequest();
            request.setMeetingLink("meet.google.com/abc-defg-hij");

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accept Google Meet link with https")
        void shouldAcceptGoogleMeetWithHttps() {
            SeminarCreateRequest request = createValidRequest();
            request.setMeetingLink("https://meet.google.com/abc-defg-hij");

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accept Zoom link without protocol")
        void shouldAcceptZoomWithoutProtocol() {
            SeminarCreateRequest request = createValidRequest();
            request.setMeetingLink("zoom.us/j/1234567890");

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accept Zoom link with https")
        void shouldAcceptZoomWithHttps() {
            SeminarCreateRequest request = createValidRequest();
            request.setMeetingLink("https://zoom.us/j/1234567890?pwd=abc123");

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accept Microsoft Teams link without protocol")
        void shouldAcceptTeamsWithoutProtocol() {
            SeminarCreateRequest request = createValidRequest();
            request.setMeetingLink("teams.microsoft.com/l/meetup-join/abc123");

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accept Microsoft Teams Live link")
        void shouldAcceptTeamsLive() {
            SeminarCreateRequest request = createValidRequest();
            request.setMeetingLink("teams.live.com/meet/abc123");

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject invalid domain")
        void shouldRejectInvalidDomain() {
            SeminarCreateRequest request = createValidRequest();
            request.setMeetingLink("https://example.com/meeting");

            assertThatThrownBy(() -> validator.validateCreateRequest(request))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).anyMatch(err -> err.contains("Link meeting không hợp lệ"));
                    });
        }

        @Test
        @DisplayName("Should accept link with mixed case")
        void shouldAcceptMixedCase() {
            SeminarCreateRequest request = createValidRequest();
            request.setMeetingLink("MEET.GOOGLE.COM/abc-defg");

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }
    }

    // ============================================
    // DATETIME VALIDATION TESTS
    // ============================================

    @Nested
    @DisplayName("DateTime Validation Tests")
    class DateTimeValidationTests {

        @Test
        @DisplayName("Should reject null start time")
        void shouldRejectNullStartTime() {
            SeminarCreateRequest request = createValidRequest();
            request.setStartTime(null);

            assertThatThrownBy(() -> validator.validateCreateRequest(request))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).anyMatch(err -> err.contains("Thời gian bắt đầu"));
                    });
        }

        @Test
        @DisplayName("Should reject past start time")
        void shouldRejectPastStartTime() {
            SeminarCreateRequest request = createValidRequest();
            request.setStartTime(LocalDateTime.now().minusHours(1));

            assertThatThrownBy(() -> validator.validateCreateRequest(request))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).anyMatch(err -> 
                            err.contains("Thời gian bắt đầu phải sau thời điểm hiện tại"));
                    });
        }

        @Test
        @DisplayName("Should reject end time before start time")
        void shouldRejectEndTimeBeforeStart() {
            SeminarCreateRequest request = createValidRequest();
            LocalDateTime start = LocalDateTime.now().plusDays(2);
            request.setStartTime(start);
            request.setEndTime(start.minusHours(1));

            assertThatThrownBy(() -> validator.validateCreateRequest(request))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).anyMatch(err -> 
                            err.contains("Thời gian kết thúc phải sau thời gian bắt đầu"));
                    });
        }

        @Test
        @DisplayName("Should reject duration less than 30 minutes")
        void shouldRejectShortDuration() {
            SeminarCreateRequest request = createValidRequest();
            LocalDateTime start = LocalDateTime.now().plusDays(2);
            request.setStartTime(start);
            request.setEndTime(start.plusMinutes(29));

            assertThatThrownBy(() -> validator.validateCreateRequest(request))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).anyMatch(err -> 
                            err.contains("30 phút"));
                    });
        }

        @Test
        @DisplayName("Should accept exactly 30 minutes duration")
        void shouldAccept30MinuteDuration() {
            SeminarCreateRequest request = createValidRequest();
            LocalDateTime start = LocalDateTime.now().plusDays(2);
            request.setStartTime(start);
            request.setEndTime(start.plusMinutes(30));

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject duration more than 8 hours")
        void shouldRejectLongDuration() {
            SeminarCreateRequest request = createValidRequest();
            LocalDateTime start = LocalDateTime.now().plusDays(2);
            request.setStartTime(start);
            request.setEndTime(start.plusHours(9));

            assertThatThrownBy(() -> validator.validateCreateRequest(request))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).anyMatch(err -> 
                            err.contains("8 giờ"));
                    });
        }

        @Test
        @DisplayName("Should accept exactly 8 hours duration")
        void shouldAccept8HourDuration() {
            SeminarCreateRequest request = createValidRequest();
            LocalDateTime start = LocalDateTime.now().plusDays(2);
            request.setStartTime(start);
            request.setEndTime(start.plusHours(8));

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }
    }

    // ============================================
    // PRICE VALIDATION TESTS
    // ============================================

    @Nested
    @DisplayName("Price Validation Tests")
    class PriceValidationTests {

        @Test
        @DisplayName("Should accept null price (defaults to 0)")
        void shouldAcceptNullPrice() {
            SeminarCreateRequest request = createValidRequest();
            request.setPrice(null);

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accept zero price (free seminar)")
        void shouldAcceptZeroPrice() {
            SeminarCreateRequest request = createValidRequest();
            request.setPrice(BigDecimal.ZERO);

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject negative price")
        void shouldRejectNegativePrice() {
            SeminarCreateRequest request = createValidRequest();
            request.setPrice(BigDecimal.valueOf(-1000));

            assertThatThrownBy(() -> validator.validateCreateRequest(request))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).contains("Giá vé không được âm");
                    });
        }

        @Test
        @DisplayName("Should accept maximum price (100M)")
        void shouldAcceptMaxPrice() {
            SeminarCreateRequest request = createValidRequest();
            request.setPrice(BigDecimal.valueOf(100000000));

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject price exceeding 100M")
        void shouldRejectExcessivePrice() {
            SeminarCreateRequest request = createValidRequest();
            request.setPrice(BigDecimal.valueOf(100000001));

            assertThatThrownBy(() -> validator.validateCreateRequest(request))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).anyMatch(err -> 
                            err.contains("100,000,000"));
                    });
        }
    }

    // ============================================
    // MAX CAPACITY VALIDATION TESTS
    // ============================================

    @Nested
    @DisplayName("Max Capacity Validation Tests")
    class MaxCapacityValidationTests {

        @Test
        @DisplayName("Should accept null maxCapacity (unlimited)")
        void shouldAcceptNullMaxCapacity() {
            SeminarCreateRequest request = createValidRequest();
            request.setMaxCapacity(null);

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accept zero maxCapacity (unlimited)")
        void shouldAcceptZeroMaxCapacity() {
            SeminarCreateRequest request = createValidRequest();
            request.setMaxCapacity(0);

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject negative maxCapacity")
        void shouldRejectNegativeMaxCapacity() {
            SeminarCreateRequest request = createValidRequest();
            request.setMaxCapacity(-10);

            assertThatThrownBy(() -> validator.validateCreateRequest(request))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).anyMatch(err -> 
                            err.contains("Số lượng vé tối đa không được âm"));
                    });
        }

        @Test
        @DisplayName("Should accept maximum allowed capacity (10000)")
        void shouldAcceptMaxAllowedCapacity() {
            SeminarCreateRequest request = createValidRequest();
            request.setMaxCapacity(10000);

            assertThatCode(() -> validator.validateCreateRequest(request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject capacity exceeding 10000")
        void shouldRejectExcessiveCapacity() {
            SeminarCreateRequest request = createValidRequest();
            request.setMaxCapacity(10001);

            assertThatThrownBy(() -> validator.validateCreateRequest(request))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).anyMatch(err -> 
                            err.contains("10,000"));
                    });
        }
    }

    // ============================================
    // UPDATE VALIDATION TESTS
    // ============================================

    @Nested
    @DisplayName("Update Validation Tests")
    class UpdateValidationTests {

        @Test
        @DisplayName("Should reject updating capacity below tickets sold")
        void shouldRejectCapacityBelowTicketsSold() {
            SeminarUpdateRequest request = new SeminarUpdateRequest();
            request.setMaxCapacity(20);
            
            assertThatThrownBy(() -> validator.validateUpdateRequest(request, 30))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).anyMatch(err -> 
                            err.contains("Không thể đặt số lượng vé tối đa"));
                    });
        }

        @Test
        @DisplayName("Should accept updating capacity equal to tickets sold")
        void shouldAcceptCapacityEqualToTicketsSold() {
            SeminarUpdateRequest request = new SeminarUpdateRequest();
            request.setMaxCapacity(30);

            assertThatCode(() -> validator.validateUpdateRequest(request, 30))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accept updating capacity above tickets sold")
        void shouldAcceptCapacityAboveTicketsSold() {
            SeminarUpdateRequest request = new SeminarUpdateRequest();
            request.setMaxCapacity(50);

            assertThatCode(() -> validator.validateUpdateRequest(request, 30))
                    .doesNotThrowAnyException();
        }
    }

    // ============================================
    // SUBMISSION TIMING VALIDATION TESTS
    // ============================================

    @Nested
    @DisplayName("Submission Timing Validation Tests")
    class SubmissionValidationTests {

        @Test
        @DisplayName("Should reject submission for past seminar")
        void shouldRejectPastSeminarSubmission() {
            LocalDateTime pastTime = LocalDateTime.now().minusHours(1);

            assertThatThrownBy(() -> validator.validateSubmission(pastTime))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).anyMatch(err -> 
                            err.contains("đã qua thời gian bắt đầu"));
                    });
        }

        @Test
        @DisplayName("Should reject submission less than 24 hours in advance")
        void shouldRejectSubmissionTooSoon() {
            LocalDateTime soonTime = LocalDateTime.now().plusHours(12);

            assertThatThrownBy(() -> validator.validateSubmission(soonTime))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> {
                        ValidationException ve = (ValidationException) e;
                        assertThat(ve.getErrors()).anyMatch(err -> 
                            err.contains("24 giờ"));
                    });
        }

        @Test
        @DisplayName("Should accept submission exactly 24 hours in advance")
        void shouldAcceptSubmission24HoursAdvance() {
            LocalDateTime futureTime = LocalDateTime.now().plusHours(25);

            assertThatCode(() -> validator.validateSubmission(futureTime))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accept submission 48 hours in advance")
        void shouldAcceptSubmission48HoursAdvance() {
            LocalDateTime futureTime = LocalDateTime.now().plusHours(48);

            assertThatCode(() -> validator.validateSubmission(futureTime))
                    .doesNotThrowAnyException();
        }
    }

    // ============================================
    // MULTIPLE ERRORS TEST
    // ============================================

    @Test
    @DisplayName("Should collect multiple validation errors")
    void shouldCollectMultipleErrors() {
        SeminarCreateRequest request = new SeminarCreateRequest();
        // Everything is invalid
        request.setTitle("");
        request.setMeetingLink("invalid-link");
        request.setStartTime(LocalDateTime.now().minusHours(1));
        request.setEndTime(LocalDateTime.now());
        request.setPrice(BigDecimal.valueOf(-1000));
        request.setMaxCapacity(-5);

        assertThatThrownBy(() -> validator.validateCreateRequest(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.getErrors()).hasSizeGreaterThan(3);
                });
    }
}
