package com.exe.skillverse_backend.portfolio_service.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class VietnamesePhoneValidator implements ConstraintValidator<VietnamesePhone, String> {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(0|\\+84)[3-9][0-9]{8}$");

    @Override
    public boolean isValid(String phone, ConstraintValidatorContext context) {
        if (phone == null || phone.trim().isEmpty()) {
            return true; // Optional field
        }
        String cleanPhone = phone.replace("\\s", "");
        return PHONE_PATTERN.matcher(cleanPhone).matches();
    }
}
