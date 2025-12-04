package com.exe.skillverse_backend.course_service.dto.purchasedto;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CoursePurchaseDTO {
    //Long id, Long courseId, Long userId, String status, BigDecimal price, String currency, String paymentId, Instant purchasedAt, String couponCode
    private Long id;
    private Long courseId;
    private Long userId;
    private String status;
    private BigDecimal price;
    private String currency;
    // private String paymentId;
    private Instant purchasedAt;
    private String couponCode;
    private String buyerName;
    private String buyerAvatarUrl;
    private String courseTitle;
}
