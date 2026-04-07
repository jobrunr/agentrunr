package ai.javaclaw.onboarding.steps;

import ai.javaclaw.agents.AgentWorkspaceResolver;
import ai.javaclaw.onboarding.AgentOnboardingProvider;
import ai.javaclaw.onboarding.AgentOnboardingProviders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class S4_AgentMdStepTest {

    @TempDir
    Path tempDir;

    @Test
    void saveConfigurationWritesAgentInstructionsToSelectedAgentWorkspace() throws IOException {
        Path rootWorkspace = tempDir.resolve("workspace");
        Files.createDirectories(rootWorkspace);
        Files.writeString(rootWorkspace.resolve("AGENT.md"), "root agent");
        Files.writeString(rootWorkspace.resolve("INFO.md"), "root info");

        String agentId = "openai-main";
        Path modelWorkspace = rootWorkspace.resolve("agents").resolve(agentId);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("agent.workspace", "file:" + rootWorkspace + "/")
                .withProperty(S2_ProviderStep.runtimeAgentKey(agentId, "provider"), "openai")
                .withProperty(S2_ProviderStep.runtimeAgentKey(agentId, "workspace"), modelWorkspace.toString());

        AgentOnboardingProvider provider = new RuntimeProvider();
        AgentOnboardingProviders providers = new AgentOnboardingProviders(new LinkedHashSet<>(java.util.List.of(provider)));
        AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(new org.springframework.core.io.DefaultResourceLoader(), environment);
        S4_AgentMdStep step = new S4_AgentMdStep(environment, providers, resolver);

        step.saveConfiguration(
                Map.of(
                        S2_ProviderStep.SESSION_PROVIDER, "openai",
                        S2_ProviderStep.SESSION_AGENT_ID, agentId,
                        S4_AgentMdStep.SESSION_AGENT_CONTENT, "model specific agent"
                ),
                mock(ai.javaclaw.configuration.ConfigurationManager.class)
        );

        assertThat(modelWorkspace.resolve("AGENT.private.md")).hasContent("model specific agent");
        assertThat(rootWorkspace.resolve("AGENT.private.md")).doesNotExist();
    }

    private static final class RuntimeProvider implements AgentOnboardingProvider {
        @Override
        public String getId() {
            return "openai";
        }

        @Override
        public String getLabel() {
            return "OpenAI";
        }

        @Override
        public String slogan() {
            return "";
        }

        @Override
        public boolean requiresApiKey() {
            return true;
        }

        @Override
        public String defaultModel() {
            return "gpt-5.4";
        }

    }
}
