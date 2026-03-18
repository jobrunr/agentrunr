package ai.agentrunr;

import ai.agentrunr.configuration.ConfigurationManager;
import ai.agentrunr.tasks.TaskManager;
import ai.agentrunr.tools.AgentEnvironment;
import ai.agentrunr.tools.CheckListTool;
import ai.agentrunr.tools.McpTool;
import ai.agentrunr.tools.TaskTool;
import org.springaicommunity.agent.tools.BraveWebSearchTool;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.tools.SmartWebFetchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Configuration
public class AgentRunrConfiguration {

    @Bean
    @ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = "unknown", matchIfMissing = true)
    public ChatModel chatModel() {
        return prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("No AI model has been configured"))));
    }

    @Bean
    @ConditionalOnProperty(name = "agent.browser.brave.api-key")
    public BraveWebSearchTool braveWebSearchTool(@Value("${agent.browser.brave.api-key}") String braveApiKey) {
        return BraveWebSearchTool.builder(braveApiKey).resultCount(15).build();
    }

    @Bean
    public TaskTool taskTool(TaskManager taskManager) {
        return TaskTool.builder().taskManager(taskManager).build();
    }

    @Bean
    @DependsOn({"mcpHeaderCustomizer"})
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                                 ChatMemory chatMemory,
                                 SyncMcpToolCallbackProvider mcpToolProvider,
                                 TaskManager taskManager,
                                 ConfigurationManager configurationManager,
                                 @Value("${agent.workspace:Unknown}") Resource workspace,
                                 Optional<BraveWebSearchTool> braveWebSearchToolsObjectProvider,
                                 @Qualifier("playwrightBrowserTool") Optional<Object> playwrightBrowserTool
    ) throws IOException {

        String agentPrompt = workspace.createRelative("AGENT.md").getContentAsString(StandardCharsets.UTF_8) + System.lineSeparator()
                + workspace.createRelative("INFO.md").getContentAsString(StandardCharsets.UTF_8) + System.lineSeparator();

        chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultSystem(p -> p.text(agentPrompt).param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info()))
                .defaultToolCallbacks(mcpToolProvider.getToolCallbacks())
                .defaultToolCallbacks(SkillsTool.builder().addSkillsDirectory(skillsDir(workspace).toString()).build())
                .defaultTools(
                        TaskTool.builder().taskManager(taskManager).build(),
                        CheckListTool.builder().build(),
                        McpTool.builder().configurationManager(configurationManager).build(),
                        //Bash execution tool
                        ShellTools.builder().build(),// built-in shell tools
                        // Read, Write and Edit files tool
                        FileSystemTools.builder().build(),// built-in file system tools
                        // Smart web fetch tool
                        SmartWebFetchTool.builder(chatClientBuilder.clone().build()).build())
                .defaultAdvisors(
                        ToolCallAdvisor.builder().build(),
                        MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().chatMemoryRepository(new InMemoryChatMemoryRepository()).build()).build()
                );

        braveWebSearchToolsObjectProvider.ifPresent(chatClientBuilder::defaultTools);
        playwrightBrowserTool.ifPresent(chatClientBuilder::defaultTools);

        return chatClientBuilder.build();
    }

    private static Path skillsDir(Resource workspace) throws IOException {
        Path skillsDir = workspace.getFilePath().resolve("skills");
        Files.createDirectories(skillsDir);
        return skillsDir;
    }
}
