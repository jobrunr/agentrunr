package ai.javaclaw.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DefaultAgent implements Agent {

    private final AgentRegistry agentRegistry;
    private final AgentChatClientFactory chatClientFactory;
    private final ObjectProvider<ChatModel> fallbackChatModelProvider;

    public DefaultAgent(AgentRegistry agentRegistry, AgentChatClientFactory chatClientFactory, ObjectProvider<ChatModel> fallbackChatModelProvider) {
        this.agentRegistry = agentRegistry;
        this.chatClientFactory = chatClientFactory;
        this.fallbackChatModelProvider = fallbackChatModelProvider;
    }

    @Override
    public String respondTo(String agentId, String conversationId, String question) {
        return resolveAgent(agentId)
                .map(configuredAgent -> chatClient(configuredAgent)
                .prompt(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, AgentConversationId.scoped(configuredAgent.id(), conversationId)))
                .call()
                .content())
                .orElseGet(() -> fallbackChatClient()
                        .prompt(question)
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                        .call()
                        .content());
    }

    @Override
    public <T> T prompt(String agentId, String conversationId, String input, Class<T> result) {
        return resolveAgent(agentId)
                .map(configuredAgent -> chatClient(configuredAgent)
                .prompt(input)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, AgentConversationId.scoped(configuredAgent.id(), conversationId)))
                .call()
                .entity(result))
                .orElseGet(() -> fallbackChatClient()
                        .prompt(input)
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                        .call()
                        .entity(result));
    }

    private ChatClient chatClient(ConfiguredAgent configuredAgent) {
        return chatClientFactory.getClient(configuredAgent);
    }

    private Optional<ConfiguredAgent> resolveAgent(String agentId) {
        return agentRegistry.findAgent(agentId)
                .or(agentRegistry::getDefaultAgent);
    }

    private ChatClient fallbackChatClient() {
        return chatClientFactory.createClient(
                fallbackChatModelProvider.getIfUnique(chatClientFactory::noModelConfiguredChatModel)
        );
    }
}
