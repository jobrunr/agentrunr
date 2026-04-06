package ai.javaclaw.api;

import ai.javaclaw.agents.AgentRegistry;
import ai.javaclaw.agents.AgentWorkspaceResolver;
import ai.javaclaw.agents.ConfiguredAgent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.List;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentsController.class)
class AgentsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentRegistry agentRegistry;

    @MockitoBean
    private AgentWorkspaceResolver agentWorkspaceResolver;

    @Test
    void agentsPageShowsConfiguredAgentsAndDefaultBadge() throws Exception {
        ConfiguredAgent configuredAgent = new ConfiguredAgent("openai-main", "openai", "gpt-5.4", "secret", "", "");
        when(agentRegistry.getAgents()).thenReturn(List.of(configuredAgent));
        when(agentRegistry.getDefaultAgentId()).thenReturn("openai-main");
        when(agentWorkspaceResolver.resolveWorkspacePath("", "openai-main")).thenReturn(Path.of("/tmp/workspace/agents/openai-main"));

        mockMvc.perform(get("/agents"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Agents")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("openai-main")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Default")));
    }
}
