package com.exe.skillverse_backend.admin_service.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBookingDashboardResponse {
    private Long totalBookings;
    private Long pendingBookings;
    private Long activeBookings;
    private Long completedBookings;
    private Long disputedBookings;
    private Long refundedBookings;
    private BigDecimal grossBookingValueVnd;
    private BigDecimal learnerNetSpendVnd;
    private BigDecimal learnerRefundedVnd;
    private BigDecimal mentorPayoutVnd;
    private BigDecimal adminCommissionVnd;
    private BigDecimal escrowHoldingVnd;
    private List<RevenuePoint> monthlyRevenue;
    private List<StatusPoint> statusBreakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenuePoint {
        private String label;
        private BigDecimal grossValueVnd;
        private BigDecimal learnerSpendVnd;
        private BigDecimal refundedVnd;
        private BigDecimal mentorPayoutVnd;
        private BigDecimal adminCommissionVnd;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusPoint {
        private String status;
        private Long count;
    }
}
