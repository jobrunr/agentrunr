package ai.javaclaw.channels.telegram;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.channels.ChannelRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramChannelTest {

    @Mock
    private TelegramClient telegramClient;

    @Mock
    private Agent agent;

    // -----------------------------------------------------------------------
    // Ignored updates
    // -----------------------------------------------------------------------

    @Test
    void ignoresUpdatesWithoutMessage() {
        TelegramChannel channel = channel("allowed_user");
        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(false);

        channel.consume(update);

        verifyNoInteractions(agent, telegramClient);
    }

    @Test
    void ignoresUpdatesWithoutText() {
        TelegramChannel channel = channel("allowed_user");
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(false);

        channel.consume(update);

        verifyNoInteractions(agent, telegramClient);
    }

    @Test
    void ignoresMessagesFromNullUsername() {
        TelegramChannel channel = channel("allowed_user");
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getFrom()).thenReturn(null);

        channel.consume(update);

        verifyNoInteractions(agent, telegramClient);
    }

    @Test
    void ignoresMessagesFromUnauthorizedUser() {
        TelegramChannel channel = channel("allowed_user");

        channel.consume(updateFromUnknownUser("other_user"));

        verify(agent, never()).respondTo(anyString(), anyString());
        verifyNoInteractions(telegramClient);
    }

    // -----------------------------------------------------------------------
    // Username matching
    // -----------------------------------------------------------------------

    @Test
    void usernameMatchingIsCaseInsensitive() throws TelegramApiException {
        TelegramChannel channel = channel("Allowed_User");
        when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

        channel.consume(updateFrom("allowed_user", "hello", 42L, null));

        verify(agent).respondTo(anyString(), anyString());
    }

    @Test
    void stripsLeadingAtFromConfiguredUsername() throws TelegramApiException {
        TelegramChannel channel = channel("@Allowed_User");
        when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

        channel.consume(updateFrom("allowed_user", "hello", 42L, null));

        verify(agent).respondTo(anyString(), anyString());
    }

    @Test
    void stripsLeadingAtFromIncomingUsername() throws TelegramApiException {
        TelegramChannel channel = channel("allowed_user");
        when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

        channel.consume(updateFrom("@allowed_user", "hello", 42L, null));

        verify(agent).respondTo(anyString(), anyString());
    }

    // -----------------------------------------------------------------------
    // Conversation ID and SendMessage
    // -----------------------------------------------------------------------

    @Test
    void usesChannelChatIdAsConversationId() throws TelegramApiException {
        TelegramChannel channel = channel("allowed_user");
        when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

        channel.consume(updateFrom("allowed_user", "hello", 42L, null));

        verify(agent).respondTo(eq("telegram-42"), eq("hello"));
    }

    @Test
    void includesMessageThreadIdInConversationId() throws TelegramApiException {
        TelegramChannel channel = channel("allowed_user");
        when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

        channel.consume(updateFrom("allowed_user", "hello", 42L, 567));

        verify(agent).respondTo(eq("telegram-42-567"), eq("hello"));
    }

    @Test
    void sendsAgentResponseToCorrectChatId() throws TelegramApiException {
        TelegramChannel channel = channel("allowed_user");
        when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

        channel.consume(updateFrom("allowed_user", "hello", 42L, null));

        verify(telegramClient).execute(argThat((SendMessage msg) ->
                "42".equals(msg.getChatId()) && "hi".equals(msg.getText())));
    }

    @Test
    void passesMessageThreadIdToSendMessage() throws TelegramApiException {
        TelegramChannel channel = channel("allowed_user");
        when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

        channel.consume(updateFrom("allowed_user", "hello", 42L, 567));

        verify(telegramClient).execute(argThat((SendMessage msg) ->
                Integer.valueOf(567).equals(msg.getMessageThreadId())));
    }

    @Test
    void doesNotSetMessageThreadIdWhenAbsent() throws TelegramApiException {
        TelegramChannel channel = channel("allowed_user");
        when(agent.respondTo(anyString(), anyString())).thenReturn("hi");

        channel.consume(updateFrom("allowed_user", "hello", 42L, null));

        verify(telegramClient).execute(argThat((SendMessage msg) ->
                msg.getMessageThreadId() == null));
    }

    // -----------------------------------------------------------------------
    // sendMessage fallback
    // -----------------------------------------------------------------------

    @Test
    void sendMessageDoesNothingWhenNoChatIdKnown() {
        TelegramChannel channel = channel("allowed_user");

        // No message has been consumed yet, so chatId is unknown
        channel.sendMessage("hello");

        verifyNoInteractions(telegramClient);
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private TelegramChannel channel(String allowedUsername) {
        return new TelegramChannel("token", allowedUsername, telegramClient, agent, new ChannelRegistry());
    }

    private Update updateFromUnknownUser(String username) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getFrom()).thenReturn(user);
        when(user.getUserName()).thenReturn(username);
        return update;
    }

    private Update updateFrom(String username, String text, long chatId, Integer messageThreadId) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getMessageThreadId()).thenReturn(messageThreadId);
        when(message.getFrom()).thenReturn(user);
        when(user.getUserName()).thenReturn(username);

        return update;
    }
}
