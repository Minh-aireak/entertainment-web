package com.MyProject.chat_service.repository.mongo;

import com.MyProject.chat_service.entity.Conversation;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationRepositoryImpl implements ConversationRepositoryCustom {

    MongoTemplate mongoTemplate;

    @Override
    public Conversation incrementSeqAndUpdateLastMessage(String conversationId, String content) {
        Query query = new Query(Criteria.where("_id").is(conversationId));
        Update update = new Update()
                .inc("totalSeq", 1)
                .set("lastMessage", content);
        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true).upsert(false);

        return mongoTemplate.findAndModify(query, update, options, Conversation.class);
    }

    @Override
    public void updateLastMessage(String conversationId, String content) {
        Query query = new Query(Criteria.where("_id").is(conversationId));
        Update update = new Update()
                .set("lastMessage", content);
        mongoTemplate.updateFirst(query, update, Conversation.class);
    }
}
