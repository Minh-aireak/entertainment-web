package com.MyProject.post.post_service.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DataUpdatePostValidator implements ConstraintValidator<DataUpdatePostConstraint, String> {

    @Override
    public void initialize(DataUpdatePostConstraint constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {
        if (s == null) return true;
        return !s.isBlank();
    }
}
