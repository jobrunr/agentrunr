package ai.javaclaw.providers.anthropic;

import ai.javaclaw.agent.AgentChatModelFactory;
import ai.javaclaw.agent.ConfiguredAgent;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClientAsync;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;


@Component
public class AnthropicAgentChatModelFactory implements AgentChatModelFactory {

    public static final String CLAUDE_CODE_OAUTH_TOKEN_PLACEHOLDER = "<claude-code-bearer-token>";


    private final ToolCallingManager toolCallingManager;
    private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;
    private final ObservationRegistry observationRegistry;

    public AnthropicAgentChatModelFactory(ToolCallingManager toolCallingManager,
                                         ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate,
                                         ObjectProvider<ObservationRegistry> observationRegistry) {
        this.toolCallingManager = toolCallingManager;
        this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate.getIfUnique(DefaultToolExecutionEligibilityPredicate::new);
        this.observationRegistry = observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP);
    }

    @Override
    public String providerId() {
        return "anthropic";
    }

    @Override
    public ChatModel createChatModel(ConfiguredAgent configuredAgent) {
        AnthropicChatOptions options = new AnthropicChatOptions();
        options.setModel(configuredAgent.model());

        // Special case: Claude Code OAuth uses a custom backend to inject the bearer token.
        if (CLAUDE_CODE_OAUTH_TOKEN_PLACEHOLDER.equals(configuredAgent.apiKey())) {
            options.setApiKey(CLAUDE_CODE_OAUTH_TOKEN_PLACEHOLDER);
            var backend = new AnthropicClaudeCodeBackend();

            return AnthropicChatModel.builder()
                    .options(options)
                    .anthropicClient(anthropicClient(options, backend))
                    .anthropicClientAsync(anthropicClientAsync(options, backend))
                    .toolCallingManager(toolCallingManager)
                    .observationRegistry(observationRegistry)
                    .toolExecutionEligibilityPredicate(toolExecutionEligibilityPredicate)
                    .build();
        }

        options.setApiKey(configuredAgent.apiKey());
        if (configuredAgent.baseUrl() != null && !configuredAgent.baseUrl().isBlank()) {
            options.setBaseUrl(configuredAgent.baseUrl());
        }

        return AnthropicChatModel.builder()
                .options(options)
                .anthropicClient(anthropicClient(options))
                .anthropicClientAsync(anthropicClientAsync(options))
                .toolCallingManager(toolCallingManager)
                .observationRegistry(observationRegistry)
                .toolExecutionEligibilityPredicate(toolExecutionEligibilityPredicate)
                .build();
    }

    private static AnthropicClient anthropicClient(AnthropicChatOptions options) {
        var clientBuilder = AnthropicOkHttpClient.builder()
                .apiKey(options.getApiKey());
        if (options.getBaseUrl() != null) clientBuilder.baseUrl(options.getBaseUrl());
        if (options.getTimeout() != null) clientBuilder.timeout(options.getTimeout());
        if (options.getMaxRetries() != null) clientBuilder.maxRetries(options.getMaxRetries());
        if (options.getProxy() != null) clientBuilder.proxy(options.getProxy());
        if (!options.getCustomHeaders().isEmpty()) {
            options.getCustomHeaders().forEach(clientBuilder::putHeader);
        }
        return clientBuilder.build();
    }

    private static AnthropicClientAsync anthropicClientAsync(AnthropicChatOptions options) {
        var asyncClientBuilder = AnthropicOkHttpClientAsync.builder()
                .apiKey(options.getApiKey());
        if (options.getBaseUrl() != null) asyncClientBuilder.baseUrl(options.getBaseUrl());
        if (options.getTimeout() != null) asyncClientBuilder.timeout(options.getTimeout());
        if (options.getMaxRetries() != null) asyncClientBuilder.maxRetries(options.getMaxRetries());
        if (options.getProxy() != null) asyncClientBuilder.proxy(options.getProxy());
        if (!options.getCustomHeaders().isEmpty()) {
            options.getCustomHeaders().forEach(asyncClientBuilder::putHeader);
        }
        return asyncClientBuilder.build();
    }

    private static AnthropicClient anthropicClient(AnthropicChatOptions options, AnthropicClaudeCodeBackend backend) {
        var clientBuilder = AnthropicOkHttpClient.builder().backend(backend);
        if (options.getTimeout() != null) clientBuilder.timeout(options.getTimeout());
        if (options.getMaxRetries() != null) clientBuilder.maxRetries(options.getMaxRetries());
        if (options.getProxy() != null) clientBuilder.proxy(options.getProxy());
        return clientBuilder.build();
    }

    private static AnthropicClientAsync anthropicClientAsync(AnthropicChatOptions options, AnthropicClaudeCodeBackend backend) {
        var asyncClientBuilder = AnthropicOkHttpClientAsync.builder().backend(backend);
        if (options.getTimeout() != null) asyncClientBuilder.timeout(options.getTimeout());
        if (options.getMaxRetries() != null) asyncClientBuilder.maxRetries(options.getMaxRetries());
        if (options.getProxy() != null) asyncClientBuilder.proxy(options.getProxy());
        return asyncClientBuilder.build();
    }
}
