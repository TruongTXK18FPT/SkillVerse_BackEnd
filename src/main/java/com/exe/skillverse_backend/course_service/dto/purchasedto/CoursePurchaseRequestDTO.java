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
    //{ @NotNull Long courseId, @NotNull BigDecimal price, String currency, String couponCode
    @NotNull
    private Long courseId;
    @NotNull
    private BigDecimal price;
    private String currency;
    private String couponCode;

}
