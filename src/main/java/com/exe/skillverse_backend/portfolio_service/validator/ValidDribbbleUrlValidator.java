package com.exe.skillverse_backend.portfolio_service.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;

public class ValidDribbbleUrlValidator implements ConstraintValidator<ValidDribbbleUrl, String> {
    @Override
    public boolean isValid(String url, ConstraintValidatorContext context) {
        if (url == null || url.trim().isEmpty()) {
            return true;
        }
        try {
            URI parsed = URI.create(url);
            if (!parsed.getHost().contains("dribbble.com")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("URL Dribbble không hợp lệ. Vui lòng nhập URL từ dribbble.com").addConstraintViolation();
                return false;
            }
            if (!parsed.getScheme().equals("https")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("URL phải sử dụng HTTPS.").addConstraintViolation();
                return false;
            }
            return true;
        } catch (Exception e) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("URL không hợp lệ.").addConstraintViolation();
            return false;
        }
    }
}
