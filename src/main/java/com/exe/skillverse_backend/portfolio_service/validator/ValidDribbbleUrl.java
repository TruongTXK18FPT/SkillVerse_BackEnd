package com.exe.skillverse_backend.portfolio_service.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidDribbbleUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDribbbleUrl {
    String message() default "URL Dribbble không hợp lệ. Vui lòng nhập URL từ dribbble.com";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
