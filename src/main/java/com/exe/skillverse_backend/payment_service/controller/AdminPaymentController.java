package com.exe.skillverse_backend.payment_service.controller;

import com.exe.skillverse_backend.payment_service.dto.response.PaymentTransactionResponse;
import com.exe.skillverse_backend.payment_service.service.PaymentService;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final WalletService walletService;
    
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
    
    /**
     * Get system-wide wallet statistics
     * Returns total cash balance, total coin balance across all users
     */
    @GetMapping("/wallet-stats")
    @Operation(summary = "Get wallet statistics", 
               description = "Get total cash and coin balance across all user wallets")
    public ResponseEntity<Map<String, Object>> getWalletStatistics() {
        log.info("Admin fetching wallet statistics");
        Map<String, Object> stats = walletService.getSystemWalletStats();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Download invoice PDF for a payment transaction
     */
    @GetMapping("/transactions/{id}/invoice")
    @Operation(summary = "Download payment invoice", 
               description = "Generate and download PDF invoice for a payment transaction")
    public ResponseEntity<byte[]> downloadPaymentInvoice(@PathVariable Long id) {
        log.info("Admin downloading invoice for payment: {}", id);
        
        byte[] pdfBytes = paymentService.generatePaymentInvoicePdf(id);
        
        String filename = "invoice-" + id + ".pdf";
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdfBytes);
    }
    
    /**
     * Download invoice PDF for a wallet transaction
     */
    @GetMapping("/wallet-transactions/{id}/invoice")
    @Operation(summary = "Download wallet transaction invoice", 
               description = "Generate and download PDF invoice for a wallet transaction")
    public ResponseEntity<byte[]> downloadWalletTransactionInvoice(@PathVariable Long id) {
        log.info("Admin downloading invoice for wallet transaction: {}", id);
        
        byte[] pdfBytes = paymentService.generateWalletTransactionInvoicePdf(id);
        
        String filename = "wallet-invoice-" + id + ".pdf";
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdfBytes);
    }
}
