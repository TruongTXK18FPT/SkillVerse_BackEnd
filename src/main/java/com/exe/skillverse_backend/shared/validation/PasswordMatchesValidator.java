package com.exe.skillverse_backend.shared.validation;

import com.exe.skillverse_backend.shared.dto.request.BaseRegistrationRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, BaseRegistrationRequest> {

    @Override
    public void initialize(PasswordMatches constraintAnnotation) {
        // Initialization method (optional)
    }

    @Override
    public boolean isValid(BaseRegistrationRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true; // Let other validations handle null objects
        }

        String password = request.getPassword();
        String confirmPassword = request.getConfirmPassword();

        // Both should be null or both should match
        if (password == null && confirmPassword == null) {
            return true;
        }

        boolean matches = password != null && password.equals(confirmPassword);

        if (!matches) {
            // Customize the error message location
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("confirmPassword")
                    .addConstraintViolation();
        }

        return matches;
    }
}