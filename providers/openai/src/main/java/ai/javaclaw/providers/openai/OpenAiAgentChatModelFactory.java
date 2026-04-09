package ai.javaclaw.providers.openai;

import ai.javaclaw.agents.AgentChatModelFactory;
import ai.javaclaw.agents.ConfiguredAgent;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

@Component
public class OpenAiAgentChatModelFactory implements AgentChatModelFactory {

    private final ToolCallingManager toolCallingManager;
    private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;
    private final RetryTemplate retryTemplate;
    private final ObservationRegistry observationRegistry;

    public OpenAiAgentChatModelFactory(ToolCallingManager toolCallingManager,
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
        return "openai";
    }

    @Override
    public ChatModel createChatModel(ConfiguredAgent configuredAgent) {
        OpenAiApi.Builder openAiApiBuilder = OpenAiApi.builder()
                .apiKey(configuredAgent.apiKey());

        if (configuredAgent.baseUrl() != null && !configuredAgent.baseUrl().isBlank()) {
            openAiApiBuilder.baseUrl(configuredAgent.baseUrl());
        }

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(configuredAgent.model())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApiBuilder.build())
                .defaultOptions(options)
                .toolCallingManager(toolCallingManager)
                .toolExecutionEligibilityPredicate(toolExecutionEligibilityPredicate)
                .retryTemplate(retryTemplate)
                .observationRegistry(observationRegistry)
                .build();
    }
}

