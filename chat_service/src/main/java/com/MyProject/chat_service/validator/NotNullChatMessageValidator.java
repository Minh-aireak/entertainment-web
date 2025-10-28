package com.MyProject.chat_service.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NotNullChatMessageValidator implements ConstraintValidator<NotNullChatMessageConstraint, String> {

    @Override
    public void initialize(NotNullChatMessageConstraint constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {
        return s != null;
    }
}
