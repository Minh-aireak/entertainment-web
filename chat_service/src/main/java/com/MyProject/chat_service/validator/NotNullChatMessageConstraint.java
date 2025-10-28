package com.MyProject.chat_service.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {NotNullChatMessageValidator.class})
public @interface NotNullChatMessageConstraint {
    String message() default "{Invalid data chat message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String attribute();
}
