package com.exe.skillverse_backend.premium_service.controller;

import com.exe.skillverse_backend.premium_service.dto.response.PremiumPlanResponse;
import com.exe.skillverse_backend.premium_service.dto.response.SubscriptionCheckoutPreviewResponse;
import com.exe.skillverse_backend.premium_service.dto.response.UserSubscriptionResponse;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/premium")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Premium Service", description = "Premium subscription management")
public class PremiumController {

    private final PremiumService premiumService;

    @GetMapping("/plans")
    @Operation(summary = "Get available premium plans (auto-filter by current user role)")
    public ResponseEntity<List<PremiumPlanResponse>> getAvailablePlans(
            @RequestParam(required = false, defaultValue = "false") boolean includeFreeTier,
            Authentication authentication) {
        log.info("Fetching available premium plans (includeFreeTier: {})", includeFreeTier);

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            Long userId = Long.valueOf(jwt.getClaimAsString("userId"));
            return ResponseEntity.ok(premiumService.getAvailablePlansForUser(userId, includeFreeTier));
        }

        return ResponseEntity.ok(premiumService.getAvailablePlansForGuest(includeFreeTier));
    }

    @GetMapping("/plans/{planId}")
    @Operation(summary = "Get premium plan by ID (role-aware visibility)")
    public ResponseEntity<PremiumPlanResponse> getPlanById(
            @Parameter(description = "Premium plan ID") @PathVariable Long planId,
            Authentication authentication) {
        log.info("Fetching premium plan with ID: {}", planId);

        Optional<PremiumPlanResponse> plan;
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            Long userId = Long.valueOf(jwt.getClaimAsString("userId"));
            plan = premiumService.getPlanByIdForUser(userId, planId);
        } else {
            plan = premiumService.getPlanByIdForGuest(planId);
        }

        return plan
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/subscription/checkout-preview")
    @Operation(summary = "Xem trước số tiền cần thanh toán cho gói Premium")
    public ResponseEntity<SubscriptionCheckoutPreviewResponse> getCheckoutPreview(
            @RequestParam Long planId,
            @Parameter(description = "Legacy compatibility flag. The backend ignores this value because pricing is resolved by backend policy.")
            @RequestParam(required = false, defaultValue = "false") Boolean applyStudentDiscount,
            @RequestParam(required = false) Long targetUserId,
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("Generating checkout preview for user {} and plan {} (targetUserId: {})",
                userId, planId, targetUserId);

        return ResponseEntity.ok(premiumService.getCheckoutPreview(
                userId,
                planId,
                applyStudentDiscount != null && applyStudentDiscount,
                targetUserId));
    }

    @GetMapping("/subscription/current")
    @Operation(summary = "Get current subscription")
    public ResponseEntity<UserSubscriptionResponse> getCurrentSubscription(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("Fetching current subscription for user: {}", userId);
        return premiumService.getCurrentSubscription(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/subscription/history")
    @Operation(summary = "Get subscription history")
    public ResponseEntity<List<UserSubscriptionResponse>> getSubscriptionHistory(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("Fetching subscription history for user: {}", userId);
        List<UserSubscriptionResponse> history = premiumService.getSubscriptionHistory(userId);

        return ResponseEntity.ok(history);
    }

    @PostMapping("/subscription/recover")
    @Operation(summary = "Try to recover PENDING subscriptions that were paid but not activated")
    public ResponseEntity<Map<String, Object>> recoverPendingSubscriptions(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("Attempting subscription recovery for user: {}", userId);
        boolean recovered = premiumService.tryRecoverPendingSubscriptions(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("recovered", recovered);
        response.put("message", recovered
                ? "Đã kích hoạt gói Premium thành công!"
                : "Không tìm thấy gói cần kích hoạt.");

        return ResponseEntity.ok(response);
    }

    @PutMapping("/subscription/cancel")
    @Operation(summary = "Hủy gói Premium hiện tại")
    public ResponseEntity<String> cancelSubscription(
            @RequestParam(required = false) String reason,
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("Cancelling subscription for user: {}", userId);
        premiumService.cancelSubscription(userId, reason != null ? reason : "User requested cancellation");

        return ResponseEntity.ok("Đã hủy gói Premium thành công.");
    }

    @GetMapping("/status")
    @Operation(summary = "Kiểm tra trạng thái Premium")
    public ResponseEntity<Boolean> checkPremiumStatus(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("Checking premium status for user: {}", userId);
        boolean hasActivePremium = premiumService.hasActivePremiumSubscription(userId);

        return ResponseEntity.ok(hasActivePremium);
    }

    @PostMapping("/purchase-with-wallet")
    @Operation(summary = "Thanh toán gói Premium bằng ví")
    public ResponseEntity<UserSubscriptionResponse> purchaseWithWallet(
            @RequestParam Long planId,
            @Parameter(description = "Legacy compatibility flag. The backend ignores this value because pricing is resolved by backend policy.")
            @RequestParam(required = false, defaultValue = "false") Boolean applyStudentDiscount,
            @RequestParam(required = false) Long targetUserId,
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("User {} purchasing premium plan {} with wallet (targetUserId: {})", userId, planId, targetUserId);
        
        try {
            UserSubscriptionResponse response = premiumService.purchaseWithWalletCash(
                userId, 
                planId, 
                applyStudentDiscount != null && applyStudentDiscount,
                targetUserId
            );
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Failed to purchase premium with wallet: {}", e.getMessage());
            throw e;
        }
    }

    @PostMapping("/subscription/enable-auto-renewal")
    @Operation(summary = "Bật gia hạn tự động cho gói Premium")
    public ResponseEntity<?> enableAutoRenewal(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("User {} requesting to enable auto-renewal", userId);
        
        try {
            premiumService.enableAutoRenewal(userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã bật gia hạn tự động thành công. Hệ thống sẽ tự động xử lý kỳ gia hạn tiếp theo theo chính sách giá hiện hành."
            ));
        } catch (RuntimeException e) {
            log.error("Failed to enable auto-renewal: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/subscription/cancel-auto-renewal")
    @Operation(summary = "Tắt gia hạn tự động, gói hiện tại vẫn tiếp tục đến hết kỳ")
    public ResponseEntity<?> cancelAutoRenewal(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("User {} requesting auto-renewal cancellation", userId);
        
        try {
            premiumService.cancelAutoRenewal(userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã hủy gia hạn tự động thành công. Gói của bạn vẫn có hiệu lực đến hết kỳ hiện tại."
            ));
        } catch (RuntimeException e) {
            log.error("Failed to cancel auto-renewal: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/subscription/cancel-with-refund")
    @Operation(summary = "Hủy gói Premium và áp dụng chính sách hoàn tiền hiện hành")
    public ResponseEntity<?> cancelSubscriptionWithRefund(
            @RequestParam(required = false) String reason,
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("User {} requesting subscription cancellation with refund", userId);
        
        try {
            double refundAmount = premiumService.cancelSubscriptionWithRefund(userId, reason);
            
            String message = refundAmount > 0 
                ? "Đã hủy gói Premium thành công. Số tiền hoàn lại: " + refundAmount + " VND"
                : "Đã hủy gia hạn tự động thành công. Quá 72 giờ nên không được hoàn tiền.";
            
            return ResponseEntity.ok(new RefundResponse(
                true,
                message,
                refundAmount
            ));
        } catch (RuntimeException e) {
            log.error("Failed to cancel subscription with refund", e);
            return ResponseEntity.badRequest().body(new RefundResponse(
                false,
                e.getMessage(),
                0.0
            ));
        }
    }

    @GetMapping("/subscription/refund-eligibility")
    @Operation(summary = "Kiểm tra điều kiện hoàn tiền của gói Premium")
    public ResponseEntity<PremiumService.RefundEligibility> checkRefundEligibility(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("Checking refund eligibility for user: {}", userId);
        PremiumService.RefundEligibility eligibility = premiumService.getRefundEligibility(userId);

        return ResponseEntity.ok(eligibility);
    }

    // Inner response classes
    private record RefundResponse(boolean success, String message, double refundAmount) {}
}
