package com.exe.skillverse_backend.course_service.dto.purchasedto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoursePurchaseRequestDTO {
    @NotNull
    private Long courseId;
    
    private BigDecimal price; // Optional, price is fetched from DB
    private String currency;
    private String couponCode;
    private String returnUrl;
    private String cancelUrl;
}
