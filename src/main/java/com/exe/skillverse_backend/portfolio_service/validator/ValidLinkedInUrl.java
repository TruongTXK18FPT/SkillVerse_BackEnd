package com.exe.skillverse_backend.portfolio_service.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidLinkedInUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidLinkedInUrl {
    String message() default "URL LinkedIn không hợp lệ. Vui lòng nhập URL từ linkedin.com";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
