package ai.javaclaw.agents;

import org.springframework.ai.chat.model.ChatModel;

/**
 * Provider-specific factory for building a {@link ChatModel} at runtime for a configured agent.
 * <p>
 * Implementations live in provider modules (e.g. {@code providers/openai}) so {@code base} does not
 * depend on provider-specific Spring AI artifacts.
 */
public interface AgentChatModelFactory {

    /**
     * Stable provider id used in config (e.g. {@code openai}, {@code anthropic}).
     */
    String providerId();

    ChatModel createChatModel(ConfiguredAgent configuredAgent);
}

