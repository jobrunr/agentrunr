package ai.javaclaw.providers.google.genai;

import ai.javaclaw.agents.AgentChatModelFactory;
import ai.javaclaw.agents.ConfiguredAgent;
import com.google.genai.Client;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

@Component
public class GoogleGenAiAgentChatModelFactory implements AgentChatModelFactory {

    private final ToolCallingManager toolCallingManager;
    private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;
    private final RetryTemplate retryTemplate;
    private final ObservationRegistry observationRegistry;

    public GoogleGenAiAgentChatModelFactory(ToolCallingManager toolCallingManager,
                                           ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate,
                                           ObjectProvider<RetryTemplate> retryTemplate,
                                           ObjectProvider<ObservationRegistry> observationRegistry) {
        this.toolCallingManager = toolCallingManager;
        this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate.getIfUnique(DefaultToolExecutionEligibilityPredicate::new);
        this.retryTemplate = retryTemplate.getIfUnique(RetryTemplate::new);
        this.observationRegistry = observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP);
    }

    @Override
    public String providerId() {
        return "google.genai";
    }

    @Override
    public ChatModel createChatModel(ConfiguredAgent configuredAgent) {
        // Note: the underlying Google GenAI Java SDK client does not support per-client baseUrl overrides
        // via the public builder API. We ignore configuredAgent.baseUrl() for now.
        Client client = Client.builder()
                .apiKey(configuredAgent.apiKey())
                .build();

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .model(configuredAgent.model())
                .build();

        return GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .defaultOptions(options)
                .toolCallingManager(toolCallingManager)
                .toolExecutionEligibilityPredicate(toolExecutionEligibilityPredicate)
                .retryTemplate(retryTemplate)
                .observationRegistry(observationRegistry)
                .build();
    }
}

