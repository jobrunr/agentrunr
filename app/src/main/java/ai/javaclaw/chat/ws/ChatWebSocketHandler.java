package ai.javaclaw.chat.ws;

import ai.javaclaw.chat.ChatChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "javaclaw.chat.transport", havingValue = "spring-websocket", matchIfMissing = true)
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ChatChannel chatChannel;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(ChatChannel chatChannel, ObjectMapper objectMapper) {
        this.chatChannel = chatChannel;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        chatChannel.setWsSession(session);
        log.info("WebChat WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        chatChannel.clearWsSession(session);
        log.info("WebChat WebSocket disconnected: {} ({})", session.getId(), status);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
        String userMessage = (String) payload.get("message");

        if (userMessage == null || userMessage.isBlank()) return;
        userMessage = userMessage.trim();

        // Echo user message + show typing indicator
        chatChannel.sendHtml(oobAppend("chat-messages", userBubble(userMessage)) +
                oobReplace("typing-indicator", typingDots())
        );

        // Call agent (blocking — background tasks may push messages via ChatChannel during this)
        String response = chatChannel.chat(userMessage);

        // Send agent response + clear typing indicator
        chatChannel.sendHtml(
                oobAppend("chat-messages", agentBubble(response)) +
                        oobReplace("typing-indicator", "")
        );
    }

    // ---- HTML helpers ----

    static String userBubble(String text) {
        return "<article class=\"ar-msg ar-msg--user\">" +
                "<div class=\"ar-msg__bubble\">" + escape(text) + "</div>" +
                "</article>";
    }

    static String agentBubble(String text) {
        return "<article class=\"ar-msg ar-msg--agent\">" +
                "<div class=\"ar-msg__avatar\">JC</div>" +
                "<div class=\"ar-msg__bubble\">" + escape(text) + "</div>" +
                "</article>";
    }

    private static String typingDots() {
        return "<div class=\"ar-typing\">" +
                "<div class=\"ar-msg__avatar\">JC</div>" +
                "<div class=\"ar-typing__dots\"><span></span><span></span><span></span></div>" +
                "</div>";
    }

    static String oobAppend(String id, String content) {
        return "<div id=\"" + id + "\" hx-swap-oob=\"beforeend\">" + content + "</div>";
    }

    static String oobReplace(String id, String content) {
        return "<div id=\"" + id + "\" hx-swap-oob=\"true\">" + content + "</div>";
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text);
    }
}
