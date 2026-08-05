package com.MyProject.chat_service.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NotNullChatMessageValidator implements ConstraintValidator<NotNullChatMessageConstraint, Object> {

    @Override
    public void initialize(NotNullChatMessageConstraint constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext constraintValidatorContext) {
        return value != null;
    }
}
