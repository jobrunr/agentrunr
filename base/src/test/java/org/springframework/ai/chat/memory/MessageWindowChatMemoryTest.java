package org.springframework.ai.chat.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class MessageWindowChatMemoryTest {

    static final String CONVERSATION_ID = "test-conv";
    static final int MAX_MESSAGES = 5;

    InMemoryChatMemoryRepository repository;
    MessageWindowChatMemory memory;

    @BeforeEach
    void setUp() {
        repository = new InMemoryChatMemoryRepository();
        memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(MAX_MESSAGES)
                .build();
    }

    // -----------------------------------------------------------------------
    // add
    // -----------------------------------------------------------------------

    @Test
    void addStoresAllMessagesInRepository() {
        memory.add(CONVERSATION_ID, List.of(new UserMessage("msg1"), new AssistantMessage("msg2")));
        memory.add(CONVERSATION_ID, List.of(new UserMessage("msg3")));

        List<Message> stored = repository.findByConversationId(CONVERSATION_ID);
        assertThat(stored).hasSize(3);
        assertThat(stored.get(0).getText()).isEqualTo("msg1");
        assertThat(stored.get(1).getText()).isEqualTo("msg2");
        assertThat(stored.get(2).getText()).isEqualTo("msg3");
    }

    @Test
    void addDoesNotTrimRepositoryWhenExceedingMaxMessages() {
        List<Message> messages = IntStream.rangeClosed(1, MAX_MESSAGES + 3)
                .mapToObj(i -> (Message) new UserMessage("msg" + i))
                .toList();

        memory.add(CONVERSATION_ID, messages);

        // repository contains ALL messages, not just last maxMessages
        List<Message> stored = repository.findByConversationId(CONVERSATION_ID);
        assertThat(stored).hasSize(MAX_MESSAGES + 3);
    }

    // -----------------------------------------------------------------------
    // get – windowed view
    // -----------------------------------------------------------------------

    @Test
    void getReturnsAllMessagesWhenBelowMaxMessages() {
        memory.add(CONVERSATION_ID, List.of(
                new UserMessage("hello"),
                new AssistantMessage("hi")
        ));

        List<Message> result = memory.get(CONVERSATION_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getText()).isEqualTo("hello");
        assertThat(result.get(1).getText()).isEqualTo("hi");
    }

    @Test
    void getReturnsAllMessagesWhenExactlyMaxMessages() {
        List<Message> messages = IntStream.rangeClosed(1, MAX_MESSAGES)
                .mapToObj(i -> (Message) new UserMessage("msg" + i))
                .toList();
        memory.add(CONVERSATION_ID, messages);

        List<Message> result = memory.get(CONVERSATION_ID);

        assertThat(result).hasSize(MAX_MESSAGES);
    }

    @Test
    void getReturnsWindowedViewWhenExceedingMaxMessages() {
        List<Message> messages = IntStream.rangeClosed(1, MAX_MESSAGES + 3)
                .mapToObj(i -> (Message) new UserMessage("msg" + i))
                .toList();
        memory.add(CONVERSATION_ID, messages);

        List<Message> result = memory.get(CONVERSATION_ID);

        assertThat(result).hasSize(MAX_MESSAGES);
        // should contain the LAST maxMessages messages
        assertThat(result.get(0).getText()).isEqualTo("msg4");
        assertThat(result.get(result.size() - 1).getText()).isEqualTo("msg" + (MAX_MESSAGES + 3));
    }

    @Test
    void getPreservesSystemMessageWhenTrimming() {
        SystemMessage system = new SystemMessage("You are a helpful assistant.");
        memory.add(CONVERSATION_ID, List.of(system));
        // add enough messages to exceed the window
        List<Message> extras = IntStream.rangeClosed(1, MAX_MESSAGES + 2)
                .mapToObj(i -> (Message) new UserMessage("msg" + i))
                .toList();
        memory.add(CONVERSATION_ID, extras);

        List<Message> result = memory.get(CONVERSATION_ID);

        // system message is always preserved
        assertThat(result).hasSize(MAX_MESSAGES);
        assertThat(result.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(result.get(0).getText()).isEqualTo("You are a helpful assistant.");
    }

    @Test
    void getWindowDoesNotMutateRepository() {
        List<Message> messages = IntStream.rangeClosed(1, MAX_MESSAGES + 5)
                .mapToObj(i -> (Message) new UserMessage("msg" + i))
                .toList();
        memory.add(CONVERSATION_ID, messages);

        // call get multiple times
        memory.get(CONVERSATION_ID);
        memory.get(CONVERSATION_ID);

        // repository still has all original messages
        assertThat(repository.findByConversationId(CONVERSATION_ID)).hasSize(MAX_MESSAGES + 5);
    }

    @Test
    void getReturnsEmptyListForUnknownConversation() {
        assertThat(memory.get("unknown")).isEmpty();
    }

    // -----------------------------------------------------------------------
    // clear
    // -----------------------------------------------------------------------

    @Test
    void clearRemovesAllMessagesFromRepository() {
        memory.add(CONVERSATION_ID, List.of(new UserMessage("hello"), new AssistantMessage("hi")));

        memory.clear(CONVERSATION_ID);

        assertThat(memory.get(CONVERSATION_ID)).isEmpty();
        assertThat(repository.findByConversationId(CONVERSATION_ID)).isEmpty();
    }

    // -----------------------------------------------------------------------
    // default maxMessages
    // -----------------------------------------------------------------------

    @Test
    void defaultMaxMessagesIs20() {
        InMemoryChatMemoryRepository defaultRepo = new InMemoryChatMemoryRepository();
        MessageWindowChatMemory memoryWithDefaultMax = MessageWindowChatMemory.builder()
                .chatMemoryRepository(defaultRepo)
                .build();

        List<Message> messages = IntStream.rangeClosed(1, 25)
                .mapToObj(i -> (Message) new UserMessage("msg" + i))
                .toList();
        memoryWithDefaultMax.add(CONVERSATION_ID, messages);

        List<Message> result = memoryWithDefaultMax.get(CONVERSATION_ID);
        assertThat(result).hasSize(20);
        assertThat(result.get(0).getText()).isEqualTo("msg6");
        assertThat(result.get(19).getText()).isEqualTo("msg25");

        assertThat(defaultRepo.findByConversationId(CONVERSATION_ID)).hasSize(25);
    }
}
