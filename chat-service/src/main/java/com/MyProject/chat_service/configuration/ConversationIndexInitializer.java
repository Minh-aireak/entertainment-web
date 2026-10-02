package com.MyProject.chat_service.configuration;

import com.MyProject.chat_service.entity.ConversationDirect;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * spring.data.mongodb.auto-index-creation is off, so ConversationDirect's @Indexed(unique = true) on
 * participantsHash was never actually created. Without it nothing stopped two concurrent "find or create"
 * calls for the same pair (the friend-accept event racing a "Nhắn tin" click, or StrictMode's double effect
 * in dev) from both inserting - one pair ended up with two direct conversations, each with its own history.
 * Only this index is created explicitly: turning auto-index-creation on would also try ChatMessage's unique
 * clientMessageId index, which existing messages (sent without a clientMessageId) would violate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationIndexInitializer {
    MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureOneDirectConversationPerPair() {
        try {
            mongoTemplate.indexOps(ConversationDirect.class).ensureIndex(new Index()
                    .on("participantsHash", Sort.Direction.ASC)
                    .unique()
                    .sparse()
                    .named("participantsHash"));
        } catch (Exception e) {
            log.error("Could not create the unique index on conversation.participantsHash - the collection "
                    + "probably already holds duplicate direct conversations for one pair; merge them and restart.", e);
        }
    }
}
