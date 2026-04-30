package com.exe.skillverse_backend.portfolio_service.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidGitHubUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidGitHubUrl {
    String message() default "URL GitHub không hợp lệ. Vui lòng nhập URL từ github.com";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
