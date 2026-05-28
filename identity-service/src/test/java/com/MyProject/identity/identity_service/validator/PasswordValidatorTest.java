package com.MyProject.identity.identity_service.validator;

import jakarta.validation.ConstraintValidatorContext;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@FieldDefaults(level = AccessLevel.PRIVATE)
@ExtendWith(MockitoExtension.class)
class PasswordValidatorTest {

    PasswordValidator passwordValidator;

    @Mock
    PasswordConstraint passwordConstraint;

    @Mock
    ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        passwordValidator = new PasswordValidator();
        when(passwordConstraint.min()).thenReturn(8);
        passwordValidator.initialize(passwordConstraint);
    }

    @Test
    void isValid_validPassword_returnsTrue() {
        String pas = "12312351351";
        boolean result = passwordValidator.isValid(pas, context);

        assertTrue(result);
    }

    @Test
    void isValid_inValidPassword_returnFalse() {
        String pas = "123";
        boolean result = passwordValidator.isValid(pas, context);

        assertFalse(result);
    }
}
