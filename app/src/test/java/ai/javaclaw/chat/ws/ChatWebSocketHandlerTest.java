package ai.javaclaw.chat.ws;

import ai.javaclaw.chat.ChatChannel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ChatWebSocketHandlerTest {

    @Test
    void handleUserMessageShowsNonTransientErrorAndClearsTypingIndicatorWhenAgentFails() throws Exception {
        ChatChannel chatChannel = mock(ChatChannel.class);
        WebSocketSession session = mock(WebSocketSession.class);
        ChatWebSocketHandler handler = new ChatWebSocketHandler(chatChannel, new ObjectMapper());

        when(chatChannel.chat("web", "hello")).thenThrow(new RuntimeException("""
                HTTP 401 - {
                    "error": {
                        "message": "Incorrect API key provided: Test.",
                        "code": "invalid_api_key"
                    }
                }
                """));

        handler.handleTextMessage(session, new TextMessage(new ObjectMapper().writeValueAsString(Map.of(
                "type", "userMessage",
                "conversationId", "web",
                "message", "hello"
        ))));

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        var inOrder = inOrder(chatChannel);
        inOrder.verify(chatChannel).sendHtml(htmlCaptor.capture());
        inOrder.verify(chatChannel).chat("web", "hello");
        inOrder.verify(chatChannel).sendHtml(htmlCaptor.capture());
        verifyNoMoreInteractions(chatChannel);

        assertThat(htmlCaptor.getAllValues().get(0))
                .contains("hello")
                .contains("typing-indicator")
                .contains("ar-typing");

        assertThat(htmlCaptor.getAllValues().get(1))
                .contains("An error occurred while contacting the AI provider")
                .contains("Details: HTTP 401 - {")
                .contains("typing-indicator")
                .doesNotContain("ar-typing");
    }

    @Test
    void handleUserMessageShowsGenericProviderErrorForUnexpectedFailures() throws Exception {
        ChatChannel chatChannel = mock(ChatChannel.class);
        WebSocketSession session = mock(WebSocketSession.class);
        ChatWebSocketHandler handler = new ChatWebSocketHandler(chatChannel, new ObjectMapper());

        when(chatChannel.chat(anyString(), anyString())).thenThrow(new RuntimeException("boom"));

        handler.handleTextMessage(session, new TextMessage(new ObjectMapper().writeValueAsString(Map.of(
                "type", "userMessage",
                "conversationId", "web",
                "message", "hello"
        ))));

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        var inOrder = inOrder(chatChannel);
        inOrder.verify(chatChannel).sendHtml(htmlCaptor.capture());
        inOrder.verify(chatChannel).chat("web", "hello");
        inOrder.verify(chatChannel).sendHtml(htmlCaptor.capture());

        assertThat(htmlCaptor.getAllValues().get(1))
                .contains("An error occurred while contacting the AI provider")
                .contains("Details: boom");
    }
}