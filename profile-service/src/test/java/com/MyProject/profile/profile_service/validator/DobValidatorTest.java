package com.MyProject.profile.profile_service.validator;

import jakarta.validation.ConstraintValidatorContext;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@FieldDefaults(level = AccessLevel.PRIVATE)
@ExtendWith(MockitoExtension.class)
class DobValidatorTest {

    DobValidator dobValidator;

    @Mock
    DobConstraint dobConstraint;

    @Mock
    ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        dobValidator = new DobValidator();
        when(dobConstraint.min()).thenReturn(18);
        dobValidator.initialize(dobConstraint);
    }

    @Test
    void isValid_validAge_returnsTrue() {
        LocalDate validDob = LocalDate.now().minusYears(18);

        boolean result = dobValidator.isValid(validDob, context);

        assertTrue(result);
    }

    @Test
    void isValid_olderThanMin_returnsTrue() {
        LocalDate validDob = LocalDate.now().minusYears(19);

        boolean result = dobValidator.isValid(validDob, context);

        assertTrue(result);
    }

    @Test
    void isValid_tooYoung_returnsFalse() {
        LocalDate invalidDob = LocalDate.now().minusYears(17);

        boolean result = dobValidator.isValid(invalidDob, context);

        assertFalse(result);
    }

    @Test
    void isValid_boundaryCheck_oneDayShort_returnsFalse() {
        LocalDate invalidDob = LocalDate.now().minusYears(18).plusDays(1);

        boolean result = dobValidator.isValid(invalidDob, context);

        assertFalse(result);
    }

    @Test
    void isValid_nullDate_returnsTrue() {
        boolean result = dobValidator.isValid(null, context);

        assertTrue(result);
    }
}
