package com.exe.skillverse_backend.portfolio_service.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidBehanceUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidBehanceUrl {
    String message() default "URL Behance không hợp lệ. Vui lòng nhập URL từ behance.net";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
