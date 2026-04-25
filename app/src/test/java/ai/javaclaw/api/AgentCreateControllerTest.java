package ai.javaclaw.api;

import ai.javaclaw.agent.AgentRegistry;
import ai.javaclaw.agent.AgentWorkspaceResolver;
import ai.javaclaw.configuration.ConfigurationManager;
import ai.javaclaw.onboarding.AgentOnboardingProvider;
import ai.javaclaw.onboarding.AgentOnboardingProviders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentCreateController.class)
class AgentCreateControllerTest {

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
    void newAgentPageRenders() throws Exception {
        AgentOnboardingProvider provider = org.mockito.Mockito.mock(AgentOnboardingProvider.class);
        when(provider.getId()).thenReturn("openai");
        when(provider.getLabel()).thenReturn("OpenAI");
        when(agentOnboardingProviders.getAll()).thenReturn(List.of(provider));
        when(configurationManager.readApplicationYaml()).thenReturn(Map.of());

        mockMvc.perform(get("/agents/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Add Agent")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OpenAI")));
    }
}
