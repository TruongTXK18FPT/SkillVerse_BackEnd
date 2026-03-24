package com.exe.skillverse_backend.business_service.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobEscrowResponse {

    private Long escrowId;
    private Long jobId;
    private String jobTitle;
    private Long recruiterId;
    private String recruiterName;
    private Long workerId;
    private String workerName;
    private BigDecimal totalAmount;
    private BigDecimal platformFee;
    private BigDecimal netAmount;
    private BigDecimal escrowBalance;
    private BigDecimal pendingPayoutBalance;
    private String status;
    private LocalDateTime fundedAt;
    private LocalDateTime releasedAt;
    private LocalDateTime refundedAt;
    private List<EscrowTransactionResponse> transactions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EscrowTransactionResponse {
        private Long id;
        private String transactionType;
        private BigDecimal amount;
        private BigDecimal feeAmount;
        private BigDecimal netAmount;
        private String actorName;
        private String reason;
        private LocalDateTime createdAt;
    }
}
