package ai.javaclaw.providers.ollama;

import ai.javaclaw.agent.AgentChatModelFactory;
import ai.javaclaw.agent.ConfiguredAgent;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

@Component
public class OllamaAgentChatModelFactory implements AgentChatModelFactory {

    private final ToolCallingManager toolCallingManager;
    private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;
    private final RetryTemplate retryTemplate;
    private final ObservationRegistry observationRegistry;

    public OllamaAgentChatModelFactory(ToolCallingManager toolCallingManager,
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
        return "ollama";
    }

    @Override
    public ChatModel createChatModel(ConfiguredAgent configuredAgent) {
        OllamaApi.Builder apiBuilder = OllamaApi.builder();
        if (configuredAgent.baseUrl() != null && !configuredAgent.baseUrl().isBlank()) {
            apiBuilder.baseUrl(configuredAgent.baseUrl());
        }

        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(configuredAgent.model())
                .build();

        return OllamaChatModel.builder()
                .ollamaApi(apiBuilder.build())
                .defaultOptions(options)
                .toolCallingManager(toolCallingManager)
                .toolExecutionEligibilityPredicate(toolExecutionEligibilityPredicate)
                .retryTemplate(retryTemplate)
                .observationRegistry(observationRegistry)
                .modelManagementOptions(ModelManagementOptions.defaults())
                .build();
    }
}

