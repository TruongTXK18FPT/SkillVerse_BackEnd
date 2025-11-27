package com.exe.skillverse_backend.payment_service.controller;

import com.exe.skillverse_backend.payment_service.dto.response.PaymentTransactionResponse;
import com.exe.skillverse_backend.payment_service.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Admin Payment Controller - Admin-only APIs for payment management
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Payments", description = "Admin payment transaction management")
public class AdminPaymentController {
    
    private final PaymentService paymentService;
    
    /**
     * Get all payment transactions with filtering
     */
    @GetMapping("/transactions")
    @Operation(summary = "Get all payment transactions", description = "Retrieve all payment transactions with optional filters")
    public ResponseEntity<Page<PaymentTransactionResponse>> getAllPaymentTransactions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Pageable pageable
    ) {
        log.info("Admin fetching payment transactions - status: {}, userId: {}", status, userId);
        
        LocalDateTime start = null;
        LocalDateTime end = null;
        
        if (startDate != null && !startDate.isEmpty()) {
            start = LocalDateTime.parse(startDate, DateTimeFormatter.ISO_DATE_TIME);
        }
        if (endDate != null && !endDate.isEmpty()) {
            end = LocalDateTime.parse(endDate, DateTimeFormatter.ISO_DATE_TIME);
        }
        
        Page<PaymentTransactionResponse> transactions = paymentService.getAllTransactionsAdmin(
            status, userId, start, end, pageable
        );
        
        return ResponseEntity.ok(transactions);
    }
    
    /**
     * Get payment transaction detail
     */
    @GetMapping("/transactions/{id}")
    @Operation(summary = "Get payment transaction detail", description = "Retrieve detailed payment transaction information")
    public ResponseEntity<PaymentTransactionResponse> getPaymentDetail(@PathVariable Long id) {
        log.info("Admin fetching payment detail for id: {}", id);
        PaymentTransactionResponse transaction = paymentService.getTransactionByIdAdmin(id);
        return ResponseEntity.ok(transaction);
    }
    
    /**
     * Get payment statistics
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get payment statistics", description = "Retrieve payment statistics for a date range")
    public ResponseEntity<Map<String, Object>> getPaymentStatistics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        log.info("Admin fetching payment statistics");
        
        LocalDateTime start = null;
        LocalDateTime end = null;
        
        if (startDate != null && !startDate.isEmpty()) {
            start = LocalDateTime.parse(startDate, DateTimeFormatter.ISO_DATE_TIME);
        }
        if (endDate != null && !endDate.isEmpty()) {
            end = LocalDateTime.parse(endDate, DateTimeFormatter.ISO_DATE_TIME);
        }
        
        Map<String, Object> stats = paymentService.getPaymentStatistics(start, end);
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get payment transactions by status
     */
    @GetMapping("/transactions/status/{status}")
    @Operation(summary = "Get transactions by status", description = "Retrieve all transactions with specific status")
    public ResponseEntity<Page<PaymentTransactionResponse>> getTransactionsByStatus(
            @PathVariable String status,
            Pageable pageable
    ) {
        log.info("Admin fetching payment transactions with status: {}", status);
        Page<PaymentTransactionResponse> transactions = paymentService.getAllTransactionsAdmin(
            status, null, null, null, pageable
        );
        return ResponseEntity.ok(transactions);
    }

    /**
     * Get revenue breakdown by time period
     * @param period - "daily", "weekly", "monthly", "yearly"
     * @param days - lookback period (days for daily/weekly, months for monthly)
     */
    @GetMapping("/revenue-breakdown")
    @Operation(summary = "Get revenue breakdown", 
               description = "Get revenue breakdown by day/week/month/year for charts")
    public ResponseEntity<Map<String, Object>> getRevenueBreakdown(
            @RequestParam(defaultValue = "daily") String period,
            @RequestParam(defaultValue = "30") int days
    ) {
        log.info("Admin fetching revenue breakdown - period: {}, days: {}", period, days);
        Map<String, Object> breakdown = paymentService.getRevenueBreakdown(period, days);
        return ResponseEntity.ok(breakdown);
    }
}
