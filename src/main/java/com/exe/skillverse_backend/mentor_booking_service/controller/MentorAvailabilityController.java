package com.exe.skillverse_backend.mentor_booking_service.controller;

import com.exe.skillverse_backend.mentor_booking_service.dto.request.AvailabilityRequest;
import com.exe.skillverse_backend.mentor_booking_service.entity.MentorAvailability;
import com.exe.skillverse_backend.mentor_booking_service.service.MentorAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/mentor-availability")
@RequiredArgsConstructor
@Tag(name = "Mentor Availability", description = "Quản lý lịch rảnh của mentor")
public class MentorAvailabilityController {

    private final MentorAvailabilityService service;

    @PostMapping
    @Operation(summary = "Thêm lịch rảnh")
    public ResponseEntity<List<MentorAvailability>> addAvailability(
            @RequestBody AvailabilityRequest request,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long mentorId = Long.valueOf(jwt.getClaimAsString("userId"));
        return ResponseEntity.ok(service.addAvailability(mentorId, request));
    }

    @GetMapping("/{mentorId}")
    @Operation(summary = "Lấy lịch rảnh của mentor")
    public ResponseEntity<List<MentorAvailability>> getAvailability(
            @PathVariable Long mentorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(service.getAvailability(mentorId, from, to));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa lịch rảnh")
    public ResponseEntity<Void> deleteAvailability(@PathVariable Long id) {
        service.deleteAvailability(id);
        return ResponseEntity.noContent().build();
    }
}
