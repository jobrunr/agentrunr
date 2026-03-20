package ai.javaclaw.chat;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelMessageReceivedEvent;
import ai.javaclaw.channels.ChannelRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * GUI channel for the web chat interface.
 * Pushes messages directly to the active WebSocket session when connected,
 * falling back to an in-memory queue for REST polling.
 */
@Component
@ConditionalOnProperty(name = "javaclaw.chat.transport", havingValue = "spring-websocket", matchIfMissing = true)
public class ChatChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(ChatChannel.class);

    private final Agent agent;
    private final ChannelRegistry channelRegistry;
    private final ConcurrentLinkedQueue<String> pendingMessages = new ConcurrentLinkedQueue<>();
    private final AtomicReference<WebSocketSession> wsSession = new AtomicReference<>();

    public ChatChannel(Agent agent, ChannelRegistry channelRegistry) {
        this.agent = agent;
        this.channelRegistry = channelRegistry;
        channelRegistry.registerChannel(this);
        log.info("Started Web Chat channel");
    }

    @Override
    public String getName() {
        return "Web Chat Channel";
    }

    /**
     * Called by the WebSocket handler when a client connects.
     */
    public void setWsSession(WebSocketSession session) {
        wsSession.set(session);
    }

    /**
     * Called by the WebSocket handler when the client disconnects.
     */
    public void clearWsSession(WebSocketSession session) {
        wsSession.compareAndSet(session, null);
    }

    /**
     * Sends a raw HTML fragment to the active WebSocket session.
     * Used by the WebSocket handler to push user/agent bubbles and typing indicators.
     */
    public void sendHtml(String html) throws IOException {
        WebSocketSession session = wsSession.get();
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(html));
        }
    }

    /**
     * Delivers a background-task message. Pushes directly to WebSocket if a session
     * is open, otherwise buffers for REST polling.
     */
    @Override
    public void sendMessage(String message) {
        try {
            sendHtml(buildBackgroundMessageHtml(message));
        } catch (IOException e) {
            log.warn("WS push failed, buffering message: {}", e.getMessage());
            pendingMessages.add(message);
        }
    }

    /**
     * Handles a chat message from the web UI.
     * Marks this channel as latest so background tasks route responses here.
     */
    public String chat(String message) {
        channelRegistry.publishMessageReceivedEvent(new ChannelMessageReceivedEvent(getName(), message));
        return agent.respondTo(message);
    }

    /**
     * Drains all pending messages from background tasks (for REST polling fallback).
     */
    public List<String> drainPendingMessages() {
        List<String> messages = new ArrayList<>();
        String msg;
        while ((msg = pendingMessages.poll()) != null) {
            messages.add(msg);
        }
        return messages;
    }

    private static String buildBackgroundMessageHtml(String text) {
        return "<div id=\"chat-messages\" hx-swap-oob=\"beforeend show:bottom\">" +
                "<article class=\"ar-msg ar-msg--agent\">" +
                "<div class=\"ar-msg__avatar\">JC</div>" +
                "<div class=\"ar-msg__bubble\">" + HtmlUtils.htmlEscape(text) + "</div>" +
                "</article></div>";
    }
}
