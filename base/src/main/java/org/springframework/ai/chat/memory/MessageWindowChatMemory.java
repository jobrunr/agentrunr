package org.springframework.ai.chat.memory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * A copy of Spring's MessageWindowChatMemory that:
 * - keeps the order of the messages (changed HashSet to LinkedHashSet)
 * - returns a windowed view instead of all messages instead of only keeping the last x messages in the repository
 * <p>
 * See https://github.com/spring-projects/spring-ai/blob/019267f/spring-ai-model/src/main/java/org/springframework/ai/chat/memory/MessageWindowChatMemory.java
 */
public class MessageWindowChatMemory implements ChatMemory {
    private static final int DEFAULT_MAX_MESSAGES = 20;

    private final AppendableChatMemoryRepository chatMemoryRepository;

    private final int maxMessages;

    private MessageWindowChatMemory(AppendableChatMemoryRepository chatMemoryRepository, int maxMessages) {
        Assert.notNull(chatMemoryRepository, "chatMemoryRepository cannot be null");
        Assert.isTrue(maxMessages > 0, "maxMessages must be greater than 0");
        this.chatMemoryRepository = chatMemoryRepository;
        this.maxMessages = maxMessages;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");

        this.chatMemoryRepository.appendAll(conversationId, messages);
    }

    @Override
    public List<Message> get(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        List<Message> allMessages = this.chatMemoryRepository.findByConversationId(conversationId);
        return window(allMessages);
    }

    @Override
    public void clear(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        this.chatMemoryRepository.deleteByConversationId(conversationId);
    }

    private List<Message> window(List<Message> messages) {
        if (messages.size() <= this.maxMessages) {
            return messages;
        }

        List<Message> systemMessages = messages.stream()
                .filter(SystemMessage.class::isInstance)
                .toList();
        List<Message> nonSystemMessages = messages.stream()
                .filter(m -> !(m instanceof SystemMessage))
                .toList();

        int maxNonSystem = Math.max(0, this.maxMessages - systemMessages.size());
        List<Message> windowedNonSystem = nonSystemMessages.subList(
                Math.max(0, nonSystemMessages.size() - maxNonSystem), nonSystemMessages.size());

        List<Message> result = new ArrayList<>(systemMessages);
        result.addAll(windowedNonSystem);
        return result;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private ChatMemoryRepository chatMemoryRepository = new InMemoryChatMemoryRepository();

        private int maxMessages = DEFAULT_MAX_MESSAGES;

        private Builder() {
        }

        public Builder chatMemoryRepository(ChatMemoryRepository chatMemoryRepository) {
            this.chatMemoryRepository = chatMemoryRepository;
            return this;
        }

        public Builder maxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        public MessageWindowChatMemory build() {
            return new MessageWindowChatMemory(new DelegatingAppendableChatMemoryRepository(this.chatMemoryRepository), this.maxMessages);
        }

    }

    private static class DelegatingAppendableChatMemoryRepository implements AppendableChatMemoryRepository {

        private final ChatMemoryRepository chatMemoryRepository;

        public DelegatingAppendableChatMemoryRepository(ChatMemoryRepository chatMemoryRepository) {
            this.chatMemoryRepository = chatMemoryRepository;
        }

        @Override
        public List<String> findConversationIds() {
            return chatMemoryRepository.findConversationIds();
        }

        @Override
        public List<Message> findByConversationId(String conversationId) {
            return chatMemoryRepository.findByConversationId(conversationId);
        }

        @Override
        public void saveAll(String conversationId, List<Message> messages) {
            chatMemoryRepository.saveAll(conversationId, messages);
        }

        @Override
        public void appendAll(String conversationId, List<Message> messages) {
            if (chatMemoryRepository instanceof AppendableChatMemoryRepository appendableChatMemoryRepository) {
                appendableChatMemoryRepository.appendAll(conversationId, messages);
            } else {
                List<Message> allMessages = new ArrayList<>();
                allMessages.addAll(findByConversationId(conversationId));
                allMessages.addAll(messages);
                saveAll(conversationId, allMessages);
            }
        }

        @Override
        public void deleteByConversationId(String conversationId) {chatMemoryRepository.deleteByConversationId(conversationId);}
    }
}
