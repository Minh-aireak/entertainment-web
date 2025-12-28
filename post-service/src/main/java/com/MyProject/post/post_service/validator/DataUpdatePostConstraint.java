package com.MyProject.post.post_service.validator;

import jakarta.validation.Constraint;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {DataUpdatePostValidator.class})
public @interface DataUpdatePostConstraint {
    String message() default "{Invalid data update post}";

    String attribute();
}
