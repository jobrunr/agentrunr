package ai.javaclaw.models;

import ai.javaclaw.agents.AgentChatClientFactory;
import ai.javaclaw.agents.AgentWorkspaceResolver;
import ai.javaclaw.configuration.ConfigurationManager;
import ai.javaclaw.tasks.TaskManager;
import ai.javaclaw.tools.AutoDiscoveredTool;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentChatClientFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void createClientSkipsSkillsToolWhenWorkspaceHasNoSkills() throws Exception {
        Path rootWorkspace = tempDir.resolve("workspace");
        Files.createDirectories(rootWorkspace);
        Files.writeString(rootWorkspace.resolve("AGENT.md"), "root agent");
        Files.writeString(rootWorkspace.resolve("INFO.md"), "root info");

        AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(
                new DefaultResourceLoader(),
                new MockEnvironment().withProperty("agent.workspace", "file:" + rootWorkspace + "/")
        );

        SyncMcpToolCallbackProvider mcpToolProvider = mock(SyncMcpToolCallbackProvider.class);
        when(mcpToolProvider.getToolCallbacks()).thenReturn(new ToolCallback[0]);

        AgentChatClientFactory factory = new AgentChatClientFactory(
                mock(ChatMemory.class),
                mcpToolProvider,
                mock(TaskManager.class),
                mock(ConfigurationManager.class),
                resolver,
                Set.<AutoDiscoveredTool<?>>of(),
                mock(ToolCallingManager.class),
                stubProvider(ObservationRegistry.NOOP),
                stubProvider(mock(ToolExecutionEligibilityPredicate.class)),
                stubProvider(new RetryTemplate()),
                stubProvider(RestClient.builder()),
                stubProvider(WebClient.builder())
        );

        ChatModel chatModel = factory.noModelConfiguredChatModel();

        assertThatNoException().isThrownBy(() -> {
            ChatClient client = factory.createClient(chatModel);
            client.prompt("hello");
        });
    }

    private static <T> ObjectProvider<T> stubProvider(T value) {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> value != null ? value : invocation.getArgument(0, java.util.function.Supplier.class).get());
        when(provider.getIfAvailable(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> value != null ? value : invocation.getArgument(0, java.util.function.Supplier.class).get());
        return provider;
    }
}
