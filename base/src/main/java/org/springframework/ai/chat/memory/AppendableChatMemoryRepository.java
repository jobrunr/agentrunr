package org.springframework.ai.chat.memory;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

public interface AppendableChatMemoryRepository extends ChatMemoryRepository {

    void appendAll(String conversationId, List<Message> messages);

}
