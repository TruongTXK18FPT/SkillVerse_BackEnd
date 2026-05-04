package com.exe.skillverse_backend.business_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for candidate to submit onboarding information (CCCD + Bank).
 * This data is collected AFTER offer acceptance and BEFORE contract creation.
 * No CCCD images are stored — only text data extracted via FPT AI OCR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingInfoRequest {

    // ==================== CCCD INFO (extracted via OCR, confirmed by user) ====================

    @NotBlank(message = "Số CCCD không được để trống")
    private String idCardNumber;

    @NotBlank(message = "Họ tên trên CCCD không được để trống")
    private String fullName;

    private String dateOfBirth; // dd/MM/yyyy format from OCR

    @NotNull(message = "Ngày cấp CCCD không được để trống")
    private LocalDate idCardDate;

    @NotBlank(message = "Nơi cấp CCCD không được để trống")
    private String idCardPlace;

    private String address; // Permanent address from CCCD

    // ==================== BANK INFO ====================

    @NotBlank(message = "Số tài khoản ngân hàng không được để trống")
    private String bankAccountNumber;

    @NotBlank(message = "Tên ngân hàng không được để trống")
    private String bankName;

    @NotBlank(message = "Tên chủ tài khoản không được để trống")
    private String bankAccountHolder;
}
