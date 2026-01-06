package com.exe.skillverse_backend.seminar_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeminarRevenueReportDTO {
    // Seminar Info
    private Long seminarId;
    private String seminarTitle;
    private String seminarImageUrl;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal ticketPrice;
    private String status;

    // Revenue Statistics
    private Integer totalTicketsSold;
    private BigDecimal grossRevenue; // Tổng thu = ticketPrice * totalTickets
    private BigDecimal platformFee; // 30% phí nền tảng
    private BigDecimal netIncome; // 70% thu nhập ròng

    // Detailed Lists
    private List<TicketSaleDetail> ticketSales;
    private List<PayoutDetail> payouts;

    // Summary Info
    private String recruiterName;
    private LocalDateTime reportGeneratedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketSaleDetail {
        private Long ticketId;
        private String buyerName;
        private String buyerEmail;
        private LocalDateTime purchasedAt;
        private BigDecimal pricePaid;
        private BigDecimal recruiterEarning; // 70% of pricePaid
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayoutDetail {
        private Long transactionId;
        private LocalDateTime payoutDate;
        private BigDecimal amount;
        private BigDecimal balanceAfter;
        private String transactionStatus;
    }
}
