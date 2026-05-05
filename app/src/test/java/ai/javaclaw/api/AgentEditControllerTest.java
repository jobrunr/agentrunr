package ai.javaclaw.api;

import ai.javaclaw.agent.AgentRegistry;
import ai.javaclaw.agent.AgentWorkspaceResolver;
import ai.javaclaw.agent.ConfiguredAgent;
import ai.javaclaw.configuration.ConfigurationManager;
import ai.javaclaw.onboarding.AgentOnboardingProvider;
import ai.javaclaw.onboarding.AgentOnboardingProviders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentCreateController.class)
class AgentEditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentRegistry agentRegistry;

    @MockitoBean
    private AgentOnboardingProviders agentOnboardingProviders;

    @MockitoBean
    private ConfigurationManager configurationManager;

    @MockitoBean
    private AgentWorkspaceResolver agentWorkspaceResolver;

    @Test
    void editAgentPageRendersWithoutApiKeyPrefill() throws Exception {
        ConfiguredAgent configured = new ConfiguredAgent("openai-main", "openai", "gpt-5.4", "secret", "https://api.example.com", "/tmp/ws");
        when(agentRegistry.findAgent("openai-main")).thenReturn(Optional.of(configured));
        when(agentRegistry.getDefaultAgentId()).thenReturn("openai-main");

        AgentOnboardingProvider provider = org.mockito.Mockito.mock(AgentOnboardingProvider.class);
        when(provider.getId()).thenReturn("openai");
        when(provider.getLabel()).thenReturn("OpenAI");
        when(agentOnboardingProviders.getAll()).thenReturn(List.of(provider));

        mockMvc.perform(get("/agents/openai-main/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Edit Agent")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Save Changes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("openai-main")));
    }
}

