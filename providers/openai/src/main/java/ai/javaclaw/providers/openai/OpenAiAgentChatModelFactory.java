package ai.javaclaw.providers.openai;

import ai.javaclaw.agent.AgentChatModelFactory;
import ai.javaclaw.agent.ConfiguredAgent;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.openaisdk.OpenAiSdkChatModel;
import org.springframework.ai.openaisdk.OpenAiSdkChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class OpenAiAgentChatModelFactory implements AgentChatModelFactory {

    private final ToolCallingManager toolCallingManager;
    private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;
    private final ObservationRegistry observationRegistry;

    public OpenAiAgentChatModelFactory(ToolCallingManager toolCallingManager,
                                       ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate,
                                       ObjectProvider<ObservationRegistry> observationRegistry) {
        this.toolCallingManager = toolCallingManager;
        this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate.getIfUnique(DefaultToolExecutionEligibilityPredicate::new);
        this.observationRegistry = observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP);
    }

    @Override
    public String providerId() {
        return "openai";
    }

    @Override
    public ChatModel createChatModel(ConfiguredAgent configuredAgent) {
        OpenAiSdkChatOptions options = OpenAiSdkChatOptions.builder()
                //.baseUrl("https://<my-deployment-url>.openai.microsoftFoundry.com/")
                .apiKey(configuredAgent.apiKey())
                .model(configuredAgent.model())
                .build();


        return OpenAiSdkChatModel.builder()
                .options(options)
                .toolCallingManager(toolCallingManager)
                .toolExecutionEligibilityPredicate(toolExecutionEligibilityPredicate)
                .observationRegistry(observationRegistry)
                .build();
    }
}

