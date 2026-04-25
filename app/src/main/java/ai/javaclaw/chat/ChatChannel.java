package ai.javaclaw.chat;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.agent.AgentConversationId;
import ai.javaclaw.agent.AgentRegistry;
import ai.javaclaw.agent.ConfiguredAgent;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelMessageReceivedEvent;
import ai.javaclaw.channels.ChannelRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

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
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "javaclaw.chat.transport", havingValue = "spring-websocket", matchIfMissing = true)
public class ChatChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(ChatChannel.class);

    private final Agent agent;
    private final AgentRegistry agentRegistry;
    private final ChannelRegistry channelRegistry;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ConcurrentLinkedQueue<String> pendingMessages = new ConcurrentLinkedQueue<>();
    private final AtomicReference<WebSocketSession> wsSession = new AtomicReference<>();

    public ChatChannel(Agent agent, AgentRegistry agentRegistry, ChannelRegistry channelRegistry, ChatMemoryRepository chatMemoryRepository) {
        this.agent = agent;
        this.agentRegistry = agentRegistry;
        this.channelRegistry = channelRegistry;
        this.chatMemoryRepository = chatMemoryRepository;
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
    public void sendHtml(String... html) throws IOException {
        WebSocketSession session = wsSession.get();
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(String.join(System.lineSeparator(), html)));
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
     * Returns all known conversation IDs, always with "web" first.
     */
    public List<String> agentIds() {
        List<String> ids = agentRegistry.getAgents().stream().map(ConfiguredAgent::id).toList();
        if (!ids.isEmpty()) {
            return ids;
        }
        return List.of(agentRegistry.getDefaultAgentId());
    }

    public String defaultAgentId() {
        return agentRegistry.getDefaultAgentId();
    }

    public List<String> conversationIds(String agentId) {
        List<String> result = new ArrayList<>();
        result.add("web");
        chatMemoryRepository.findConversationIds().stream()
                .filter(id -> agentId.equals(AgentConversationId.agentId(id)))
                .map(AgentConversationId::rawConversationId)
                .filter(id -> !id.equals("web"))
                .forEach(result::add);
        return result;
    }

    /**
     * Loads conversation history for the given conversationId as HTML bubbles.
     * Returns a single welcome bubble if no history exists yet.
     */
    public List<String> loadHistoryAsHtml(String agentId, String conversationId) {
        List<Message> history = chatMemoryRepository.findByConversationId(AgentConversationId.scoped(agentId, conversationId));
        if (history.isEmpty()) {
            return List.of(ChatHtml.agentBubble("Hi! I'm your JavaClaw assistant. How can I help you today?"));
        }
        List<String> bubbles = new ArrayList<>();
        for (Message msg : history) {
            if (msg instanceof UserMessage) bubbles.add(ChatHtml.userBubble(msg.getText()));
            else if (msg instanceof AssistantMessage) bubbles.add(ChatHtml.agentBubble(msg.getText()));
        }
        return bubbles;
    }

    /**
     * Handles a chat message from the web UI for the given conversationId.
     */
    public String chat(String agentId, String conversationId, String message) {
        channelRegistry.publishMessageReceivedEvent(new ChannelMessageReceivedEvent(getName(), message));
        return agent.respondTo(agentId, conversationId, message);
    }

    private static String buildBackgroundMessageHtml(String text) {
        return Htmx.oobAppend("chat-messages", ChatHtml.agentBubble(text));
    }
}
