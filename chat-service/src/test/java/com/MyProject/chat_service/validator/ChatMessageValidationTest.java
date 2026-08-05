package com.MyProject.chat_service.validator;

import com.MyProject.chat_service.dto.request.ChatMessageCreateRequest;
import com.MyProject.chat_service.dto.request.ChatMessageUpdateRequest;
import com.MyProject.chat_service.enums.MessageType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageValidationTest {
    private ValidatorFactory validatorFactory;
    private Validator validator;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void acceptsEnumValueForNotNullConstraint() {
        ChatMessageCreateRequest request = ChatMessageCreateRequest.builder()
                .conversationId("conversation-id")
                .messageType(MessageType.TEXT)
                .build();

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsNullMessageTypeAndKeepsCustomAttribute() {
        ChatMessageCreateRequest request = ChatMessageCreateRequest.builder()
                .conversationId("conversation-id")
                .messageType(null)
                .build();

        Set<ConstraintViolation<ChatMessageCreateRequest>> violations = validator.validate(request);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getMessage()).isEqualTo("ATTRIBUTE_NOT_NULL");
            assertThat(violation.getConstraintDescriptor().getAttributes())
                    .containsEntry("attribute", "Message Type");
        });
    }

    @Test
    void rejectsBlankUpdatedContent() {
        ChatMessageUpdateRequest request = ChatMessageUpdateRequest.builder()
                .chatMessageId("message-id")
                .content("   ")
                .build();

        Set<ConstraintViolation<ChatMessageUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getMessage()).isEqualTo("ATTRIBUTE_NOT_BLANK");
            assertThat(violation.getConstraintDescriptor().getAttributes())
                    .containsEntry("attribute", "Content");
        });
    }
}
