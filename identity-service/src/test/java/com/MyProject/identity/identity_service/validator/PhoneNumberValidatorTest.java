package com.MyProject.identity.identity_service.validator;

import jakarta.validation.ConstraintValidatorContext;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@FieldDefaults(level = AccessLevel.PRIVATE)
@ExtendWith(MockitoExtension.class)
class PhoneNumberValidatorTest {

    PhoneNumberValidator validator;

    @Mock
    ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new PhoneNumberValidator();
    }

    @Test
    void isValid_validPhoneNumber_returnsTrue() {
        String phone = "0987654321";

        boolean result = validator.isValid(phone, context);

        assertTrue(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1234567890",
            "098765432",
            "09876543211",
            "0a87654321",
            " 0987654321",
            "098765432 ",
            ""
    })
    void isValid_invalidPhoneNumber_returnsFalse(String invalidPhone) {
        boolean result = validator.isValid(invalidPhone, context);

        assertFalse(result, "Phone '" + invalidPhone + "' should be invalid");
    }

    @Test
    void isValid_nullPhone_returnsFalse() {
        boolean result = validator.isValid(null, context);

        assertFalse(result);
    }
}
