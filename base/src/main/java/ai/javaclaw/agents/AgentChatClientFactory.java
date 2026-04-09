package ai.javaclaw.agents;

import ai.javaclaw.configuration.ConfigurationChangedEvent;
import ai.javaclaw.configuration.ConfigurationManager;
import ai.javaclaw.tasks.TaskManager;
import ai.javaclaw.tools.AgentEnvironment;
import ai.javaclaw.tools.AutoDiscoveredTool;
import ai.javaclaw.tools.CheckListTool;
import ai.javaclaw.tools.McpTool;
import ai.javaclaw.tools.TaskTool;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.tools.SmartWebFetchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

@Component
public class AgentChatClientFactory {

    private static final String NO_MODEL_MESSAGE = "No AI model has been configured. If you did configure a model recently, restart JavaClaw manually for the changes to take effect.";

    private final ChatMemory chatMemory;
    private final SyncMcpToolCallbackProvider mcpToolProvider;
    private final TaskManager taskManager;
    private final ConfigurationManager configurationManager;
    private final AgentWorkspaceResolver agentWorkspaceResolver;
    private final Set<AutoDiscoveredTool<?>> autoDiscoveredTools;
    private final ConcurrentMap<String, ChatClient> cachedClients = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Runnable> cachedClientDisposers = new ConcurrentHashMap<>();
    private final Map<String, AgentChatModelFactory> modelFactoriesByProviderId;

    public AgentChatClientFactory(ChatMemory chatMemory,
                                  SyncMcpToolCallbackProvider mcpToolProvider,
                                  TaskManager taskManager,
                                  ConfigurationManager configurationManager,
                                  AgentWorkspaceResolver agentWorkspaceResolver,
                                  Set<AutoDiscoveredTool<?>> autoDiscoveredTools,
                                  Set<AgentChatModelFactory> modelFactories) {
        this.chatMemory = chatMemory;
        this.mcpToolProvider = mcpToolProvider;
        this.taskManager = taskManager;
        this.configurationManager = configurationManager;
        this.agentWorkspaceResolver = agentWorkspaceResolver;
        this.autoDiscoveredTools = autoDiscoveredTools;
        this.modelFactoriesByProviderId = modelFactories.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        f -> normalizeProviderId(f.providerId()),
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException("Multiple AgentChatModelFactory beans found for providerId=" + a.providerId());
                        }
                ));
    }

    public ChatClient getClient(ConfiguredAgent configuredAgent) {
        return cachedClients.computeIfAbsent(configuredAgent.id(), ignored -> createRuntimeClient(configuredAgent));
    }

    public ChatClient createClient(ChatModel chatModel) {
        return createClient(chatModel, agentWorkspaceResolver.rootWorkspace());
    }

    private ChatClient createClient(ChatModel chatModel, Resource workspace) {
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        configure(builder, chatModel, workspace);
        return builder.build();
    }

    public ChatModel noModelConfiguredChatModel() {
        return _ -> new ChatResponse(List.of(new Generation(new AssistantMessage(NO_MODEL_MESSAGE))));
    }

    @EventListener
    public void onConfigurationChanged(ConfigurationChangedEvent ignored) {
        cachedClientDisposers.values().forEach(disposer -> {
            try {
                disposer.run();
            } catch (Exception e) {
                // best-effort cleanup; do not fail config refresh
            }
        });
        cachedClients.clear();
        cachedClientDisposers.clear();
    }

    private ChatClient createRuntimeClient(ConfiguredAgent configuredAgent) {
        AgentChatModelFactory factory = modelFactoriesByProviderId.get(normalizeProviderId(configuredAgent.provider()));
        if (factory == null) {
            return createClient(noModelConfiguredChatModel());
        }
        ChatModel chatModel = factory.createChatModel(configuredAgent);
        cachedClientDisposers.put(configuredAgent.id(), toDisposer(chatModel));
        return createClient(chatModel, resolveWorkspace(configuredAgent));
    }

    private void configure(ChatClient.Builder builder, ChatModel chatModel, Resource workspace) {
        String agentPrompt;
        String skillsDirectory;
        try {
            agentPrompt = loadAgentPrompt(workspace);
            skillsDirectory = skillsDir(workspace).toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load agent prompt", e);
        }

        builder.defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultSystem(p -> p.text(agentPrompt).param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info()))
                .defaultToolCallbacks(mcpToolProvider.getToolCallbacks())
                .defaultTools(
                        TaskTool.builder().taskManager(taskManager).build(),
                        CheckListTool.builder().build(),
                        McpTool.builder().configurationManager(configurationManager).build(),
                        FileSystemTools.builder().build(),
                        SmartWebFetchTool.builder(ChatClient.builder(chatModel).build()).build())
                .defaultAdvisors(
                        ToolCallAdvisor.builder().build(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                );

        if (hasSkillsDirectoryContent(Path.of(skillsDirectory))) {
            builder.defaultToolCallbacks(SkillsTool.builder().addSkillsDirectory(skillsDirectory).build());
        }

        autoDiscoveredTools.forEach(autoDiscoveredTool -> builder.defaultTools(autoDiscoveredTool.tool()));
    }

    private String loadAgentPrompt(Resource workspace) throws IOException {
        Resource agentMd = workspace.createRelative(ai.javaclaw.JavaClawConfiguration.AGENT_PRIVATE_MD);
        if (!agentMd.exists()) {
            agentMd = workspace.createRelative(ai.javaclaw.JavaClawConfiguration.AGENT_MD);
        }
        return agentMd.getContentAsString(StandardCharsets.UTF_8)
                + System.lineSeparator()
                + workspace.createRelative(ai.javaclaw.JavaClawConfiguration.INFO_MD).getContentAsString(StandardCharsets.UTF_8)
                + System.lineSeparator();
    }

    private Resource resolveWorkspace(ConfiguredAgent configuredAgent) {
        try {
            Path workspacePath = agentWorkspaceResolver.initializeWorkspace(
                    agentWorkspaceResolver.resolveWorkspacePath(configuredAgent.workspacePath(), configuredAgent.id())
            );
            return agentWorkspaceResolver.asResource(workspacePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize workspace for agent " + configuredAgent.id(), e);
        }
    }

    private static Path skillsDir(Resource workspace) throws IOException {
        Path skillsDir = workspace.getFilePath().resolve("skills");
        Files.createDirectories(skillsDir);
        return skillsDir;
    }

    private static boolean hasSkillsDirectoryContent(Path skillsDir) {
        try (var paths = Files.walk(skillsDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .anyMatch(path -> path.getFileName().toString().equals("SKILL.md"));
        } catch (IOException e) {
            return false;
        }
    }

    private static String normalizeProviderId(String providerId) {
        if (providerId == null) {
            return "";
        }
        return providerId.trim().toLowerCase(Locale.ROOT);
    }

    private static Runnable toDisposer(ChatModel chatModel) {
        if (chatModel instanceof DisposableBean disposableBean) {
            return () -> {
                try {
                    disposableBean.destroy();
                } catch (Exception ignored) {
                }
            };
        }
        if (chatModel instanceof AutoCloseable autoCloseable) {
            return () -> {
                try {
                    autoCloseable.close();
                } catch (Exception ignored) {
                }
            };
        }
        return () -> { };
    }
}
