package com.exe.skillverse_backend.ai_service.controller;

import com.exe.skillverse_backend.ai_service.dto.request.GenerateRoadmapRequest;
import com.exe.skillverse_backend.ai_service.dto.request.UpdateProgressRequest;
import com.exe.skillverse_backend.ai_service.dto.response.ProgressResponse;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapSessionSummary;
import com.exe.skillverse_backend.ai_service.dto.response.ValidationResult;
import com.exe.skillverse_backend.ai_service.dto.response.ClarificationQuestion;
import com.exe.skillverse_backend.ai_service.service.AiRoadmapService;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for AI-powered roadmap generation and management
 */
@RestController
@RequestMapping("/api/v1/ai/roadmap")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Roadmap", description = "AI-powered personalized learning roadmap generation")
@SecurityRequirement(name = "bearerAuth")
public class RoadmapController {

    private final AiRoadmapService aiRoadmapService;
    private final UserRepository userRepository;

    /**
     * Generate a new personalized learning roadmap using AI
     * 
     * @param request        Roadmap generation parameters
     * @param authentication Current authenticated user
     * @return Generated roadmap with session ID
     */
    @PostMapping("/generate")
    @Operation(summary = "Generate AI Roadmap", description = "Generate a personalized learning roadmap using Gemini AI based on user's goal, duration, experience, and learning style")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tạo lộ trình thành công"),
            @ApiResponse(responseCode = "400", description = "Yêu cầu không hợp lệ hoặc mục tiêu bị từ chối"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "429", description = "Vượt giới hạn sử dụng"),
            @ApiResponse(responseCode = "500", description = "Lỗi hệ thống")
    })
    public ResponseEntity<RoadmapResponse> generateRoadmap(
            @Valid @RequestBody GenerateRoadmapRequest request,
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

        String goalOrTarget = (request.getTarget() != null && !request.getTarget().isBlank()) ? request.getTarget() : request.getGoal();
        log.info("User {} requesting roadmap generation for goal/target: {} | type: {}", userId, goalOrTarget, request.getRoadmapType());

        RoadmapResponse response = aiRoadmapService.generateRoadmap(request, user);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Pre-validate roadmap generation request without actually generating
     * 
     * @param request Roadmap generation parameters to validate
     * @return List of validation warnings (INFO/WARNING/ERROR severity)
     */
    @PostMapping("/validate")
    @Operation(summary = "Pre-validate Roadmap Request", description = "Validate user inputs before generating roadmap. Returns warnings for deprecated technologies, time feasibility issues, test score validation, etc. ERROR severity blocks generation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Xác thực trước thành công"),
            @ApiResponse(responseCode = "400", description = "Yêu cầu không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    })
    public ResponseEntity<List<ValidationResult>> preValidate(
            @Valid @RequestBody GenerateRoadmapRequest request) {

        String vGoalOrTarget = (request.getTarget() != null && !request.getTarget().isBlank()) ? request.getTarget() : request.getGoal();
        log.info("Pre-validating roadmap request for goal/target: {} | type: {}", vGoalOrTarget, request.getRoadmapType());

        List<ValidationResult> warnings = aiRoadmapService.preValidateRequest(request);

        return ResponseEntity.ok(warnings);
    }

    @GetMapping("/analytics/mode-counts")
    @Operation(summary = "Analytics: Global Mode Counts", description = "Đếm số lượng roadmap theo từng mode (SKILL_BASED, CAREER_BASED) cho admin/dashboard")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    })
    public ResponseEntity<java.util.Map<String, Long>> getGlobalModeCounts() {
        java.util.Map<String, Long> counts = aiRoadmapService.getModeCountsGlobal();
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/analytics/mode-counts/me")
    @Operation(summary = "Analytics: My Mode Counts", description = "Đếm số lượng roadmap theo từng mode cho người dùng hiện tại")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    })
    public ResponseEntity<java.util.Map<String, Long>> getMyModeCounts(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));
        java.util.Map<String, Long> counts = aiRoadmapService.getModeCountsForUser(userId);
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/analytics/mode-counts/range")
    @Operation(summary = "Analytics: Mode Counts in Range", description = "Đếm số lượng roadmap theo từng mode trong khoảng thời gian [from, to]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    })
    public ResponseEntity<java.util.Map<String, Long>> getModeCountsInRange(
            @org.springframework.web.bind.annotation.RequestParam String from,
            @org.springframework.web.bind.annotation.RequestParam String to) {
        java.time.Instant fromInst = parseInstantStart(from);
        java.time.Instant toInst = parseInstantEnd(to);
        java.util.Map<String, Long> counts = aiRoadmapService.getModeCountsGlobalRange(fromInst, toInst);
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/analytics/mode-counts/me/range")
    @Operation(summary = "Analytics: My Mode Counts in Range", description = "Đếm số lượng roadmap theo mode của người dùng hiện tại trong khoảng thời gian [from, to]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    })
    public ResponseEntity<java.util.Map<String, Long>> getMyModeCountsInRange(
            @org.springframework.web.bind.annotation.RequestParam String from,
            @org.springframework.web.bind.annotation.RequestParam String to,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));
        java.time.Instant fromInst = parseInstantStart(from);
        java.time.Instant toInst = parseInstantEnd(to);
        java.util.Map<String, Long> counts = aiRoadmapService.getModeCountsForUserRange(userId, fromInst, toInst);
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/analytics/mode-counts/daily")
    @Operation(summary = "Analytics: Daily Mode Counts", description = "Nhóm theo ngày trong khoảng thời gian [from, to]")
    public ResponseEntity<java.util.Map<String, java.util.Map<String, Long>>> getDailyModeCounts(
            @org.springframework.web.bind.annotation.RequestParam String from,
            @org.springframework.web.bind.annotation.RequestParam String to) {
        java.time.Instant fromInst = parseInstantStart(from);
        java.time.Instant toInst = parseInstantEnd(to);
        return ResponseEntity.ok(aiRoadmapService.getModeCountsDaily(fromInst, toInst));
    }

    @GetMapping("/analytics/mode-counts/weekly")
    @Operation(summary = "Analytics: Weekly Mode Counts", description = "Nhóm theo tuần trong khoảng thời gian [from, to]")
    public ResponseEntity<java.util.Map<String, java.util.Map<String, Long>>> getWeeklyModeCounts(
            @org.springframework.web.bind.annotation.RequestParam String from,
            @org.springframework.web.bind.annotation.RequestParam String to) {
        java.time.Instant fromInst = parseInstantStart(from);
        java.time.Instant toInst = parseInstantEnd(to);
        return ResponseEntity.ok(aiRoadmapService.getModeCountsWeekly(fromInst, toInst));
    }

    @GetMapping("/analytics/mode-counts/monthly")
    @Operation(summary = "Analytics: Monthly Mode Counts", description = "Nhóm theo tháng trong khoảng thời gian [from, to]")
    public ResponseEntity<java.util.Map<String, java.util.Map<String, Long>>> getMonthlyModeCounts(
            @org.springframework.web.bind.annotation.RequestParam String from,
            @org.springframework.web.bind.annotation.RequestParam String to) {
        java.time.Instant fromInst = parseInstantStart(from);
        java.time.Instant toInst = parseInstantEnd(to);
        return ResponseEntity.ok(aiRoadmapService.getModeCountsMonthly(fromInst, toInst));
    }

    @GetMapping("/analytics/mode-counts/me/daily")
    @Operation(summary = "Analytics: My Daily Mode Counts", description = "Nhóm theo ngày cho người dùng hiện tại")
    public ResponseEntity<java.util.Map<String, java.util.Map<String, Long>>> getMyDailyModeCounts(
            @org.springframework.web.bind.annotation.RequestParam String from,
            @org.springframework.web.bind.annotation.RequestParam String to,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));
        java.time.Instant fromInst = parseInstantStart(from);
        java.time.Instant toInst = parseInstantEnd(to);
        return ResponseEntity.ok(aiRoadmapService.getModeCountsDailyForUser(userId, fromInst, toInst));
    }

    @GetMapping("/analytics/mode-counts/me/weekly")
    @Operation(summary = "Analytics: My Weekly Mode Counts", description = "Nhóm theo tuần cho người dùng hiện tại")
    public ResponseEntity<java.util.Map<String, java.util.Map<String, Long>>> getMyWeeklyModeCounts(
            @org.springframework.web.bind.annotation.RequestParam String from,
            @org.springframework.web.bind.annotation.RequestParam String to,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));
        java.time.Instant fromInst = parseInstantStart(from);
        java.time.Instant toInst = parseInstantEnd(to);
        return ResponseEntity.ok(aiRoadmapService.getModeCountsWeeklyForUser(userId, fromInst, toInst));
    }

    @GetMapping("/analytics/mode-counts/me/monthly")
    @Operation(summary = "Analytics: My Monthly Mode Counts", description = "Nhóm theo tháng cho người dùng hiện tại")
    public ResponseEntity<java.util.Map<String, java.util.Map<String, Long>>> getMyMonthlyModeCounts(
            @org.springframework.web.bind.annotation.RequestParam String from,
            @org.springframework.web.bind.annotation.RequestParam String to,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));
        java.time.Instant fromInst = parseInstantStart(from);
        java.time.Instant toInst = parseInstantEnd(to);
        return ResponseEntity.ok(aiRoadmapService.getModeCountsMonthlyForUser(userId, fromInst, toInst));
    }

    private java.time.Instant parseInstantStart(String s) {
        try {
            return java.time.Instant.parse(s);
        } catch (Exception ignored) {
        }
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(s);
            return d.atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        } catch (Exception ignored) {
        }
        return java.time.Instant.now().minus(java.time.Duration.ofDays(30));
    }

    private java.time.Instant parseInstantEnd(String s) {
        try {
            return java.time.Instant.parse(s);
        } catch (Exception ignored) {
        }
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(s);
            return d.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().minusSeconds(1);
        } catch (Exception ignored) {
        }
        return java.time.Instant.now();
    }

    @PostMapping("/clarify")
    @Operation(summary = "Clarify Roadmap Request", description = "Sinh ra danh sách câu hỏi làm rõ thông tin trước khi generate roadmap theo chuẩn Skillverse")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sinh câu hỏi làm rõ thành công"),
            @ApiResponse(responseCode = "400", description = "Yêu cầu không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    })
    public ResponseEntity<List<ClarificationQuestion>> clarify(
            @Valid @RequestBody GenerateRoadmapRequest request) {

        log.info("Generating clarification questions for goal/target: {}", (request.getTarget() != null && !request.getTarget().isBlank()) ? request.getTarget() : request.getGoal());

        List<ClarificationQuestion> questions = aiRoadmapService.generateClarificationQuestions(request);

        return ResponseEntity.ok(questions);
    }

    /**
     * Get all roadmap sessions for the current user
     * 
     * @param authentication Current authenticated user
     * @return List of roadmap session summaries
     */
    @GetMapping
    @Operation(summary = "Get User Roadmaps", description = "Retrieve all roadmap sessions for the current user with progress statistics")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách lộ trình thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    })
    public ResponseEntity<List<RoadmapSessionSummary>> getUserRoadmaps(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("Fetching roadmaps for user {}", userId);

        List<RoadmapSessionSummary> roadmaps = aiRoadmapService.getUserRoadmaps(userId);

        return ResponseEntity.ok(roadmaps);
    }

    /**
     * Get a specific roadmap session by ID
     * 
     * @param sessionId      Roadmap session ID
     * @param authentication Current authenticated user
     * @return Full roadmap details with nodes
     */
    @GetMapping("/{sessionId}")
    @Operation(summary = "Get Roadmap by ID", description = "Retrieve a specific roadmap session with full node details")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy lộ trình thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy lộ trình")
    })
    public ResponseEntity<RoadmapResponse> getRoadmapById(
            @PathVariable Long sessionId,
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("User {} fetching roadmap {}", userId, sessionId);

        RoadmapResponse response = aiRoadmapService.getRoadmapById(sessionId, userId);

        return ResponseEntity.ok(response);
    }

    /**
     * Update quest/milestone progress in a roadmap
     * 
     * @param sessionId      Roadmap session ID
     * @param request        Progress update details (questId, completed status)
     * @param authentication Current authenticated user
     * @return Updated progress information with statistics
     */
    @PostMapping("/{sessionId}/progress")
    @Operation(summary = "Update Quest Progress", description = "Mark a quest/milestone as completed or incomplete and get updated progress statistics")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật tiến độ thành công"),
            @ApiResponse(responseCode = "400", description = "Yêu cầu không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy lộ trình hoặc nhiệm vụ")
    })
    public ResponseEntity<ProgressResponse> updateProgress(
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateProgressRequest request,
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("User {} updating progress for session {} - quest: {}, completed: {}",
                userId, sessionId, request.getQuestId(), request.getCompleted());

        ProgressResponse response = aiRoadmapService.updateProgress(sessionId, userId, request);

        return ResponseEntity.ok(response);
    }
}
