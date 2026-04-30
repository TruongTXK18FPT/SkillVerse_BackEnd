package com.exe.skillverse_backend.portfolio_service.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

public class ValidSlugValidator implements ConstraintValidator<ValidSlug, String> {
    private static final Set<String> RESERVED_SLUGS = Set.of("create", "api", "admin", "www", "portfolio");
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 60;

    @Override
    public boolean isValid(String slug, ConstraintValidatorContext context) {
        if (slug == null || slug.trim().isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Đường dẫn tùy chỉnh là bắt buộc.").addConstraintViolation();
            return false;
        }

        String cleanSlug = slug.trim().toLowerCase();

        if (cleanSlug.length() < MIN_LENGTH) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Đường dẫn tùy chỉnh phải có ít nhất 3 ký tự.").addConstraintViolation();
            return false;
        }

        if (cleanSlug.length() > MAX_LENGTH) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Đường dẫn tùy chỉnh không được quá 60 ký tự.").addConstraintViolation();
            return false;
        }

        if (cleanSlug.matches("^[0-9-]+$")) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Đường dẫn tùy chỉnh không được chỉ chứa số và dấu gạch ngang.").addConstraintViolation();
            return false;
        }

        if (cleanSlug.startsWith("-") || cleanSlug.endsWith("-")) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Đường dẫn tùy chỉnh không được bắt đầu hoặc kết thúc bằng dấu gạch ngang.").addConstraintViolation();
            return false;
        }

        if (cleanSlug.contains("--")) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Đường dẫn tùy chỉnh không được có hai dấu gạch ngang liên tiếp.").addConstraintViolation();
            return false;
        }

        if (!cleanSlug.matches("^[a-z0-9-]+$")) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Đường dẫn tùy chỉnh chỉ được chứa chữ thường, số và dấu gạch ngang.").addConstraintViolation();
            return false;
        }

        if (RESERVED_SLUGS.contains(cleanSlug)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("\"" + cleanSlug + "\" là đường dẫn dự trữ của hệ thống. Vui lòng chọn đường dẫn khác.").addConstraintViolation();
            return false;
        }

        return true;
    }
}
