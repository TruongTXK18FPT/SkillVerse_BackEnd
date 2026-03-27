package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.purchasedto.CoursePurchaseDTO;
import com.exe.skillverse_backend.course_service.dto.purchasedto.CoursePurchaseRequestDTO;
import com.exe.skillverse_backend.course_service.service.CoursePurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/course-purchases")
@RequiredArgsConstructor
@Tag(name = "Course Purchases", description = "APIs for purchasing courses")
public class CoursePurchaseController {

    private final CoursePurchaseService coursePurchaseService;

    @PostMapping("/wallet")
    @Operation(summary = "Purchase course using wallet balance")
    public ResponseEntity<CoursePurchaseDTO> purchaseWithWallet(
            @Valid @RequestBody CoursePurchaseRequestDTO request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(coursePurchaseService.purchaseWithWallet(userId, request));
    }

    @GetMapping("/mentor")
    @Operation(summary = "Get course purchases for mentor's courses")
    public ResponseEntity<Page<CoursePurchaseDTO>> getMentorPurchases(
            Pageable pageable,
            Authentication authentication) {
        Long mentorId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(coursePurchaseService.getMentorPurchases(mentorId, pageable));
    }
}
