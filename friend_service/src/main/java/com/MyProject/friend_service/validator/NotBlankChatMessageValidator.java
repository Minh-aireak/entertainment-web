package com.MyProject.friend_service.validator;

import com.MyProject.chat_service.validator.NotBlankChatMessageConstraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NotBlankChatMessageValidator implements ConstraintValidator<NotBlankChatMessageConstraint, String> {

    @Override
    public void initialize(NotBlankChatMessageConstraint constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {
        if (s == null) return true;
        return !s.isBlank();
    }
}
