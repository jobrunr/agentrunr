package ai.javaclaw.onboarding.steps;

import ai.javaclaw.configuration.ConfigurationManager;
import ai.javaclaw.agents.AgentWorkspaceResolver;
import ai.javaclaw.onboarding.AgentOnboardingProviders;
import ai.javaclaw.onboarding.OnboardingProvider;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import static ai.javaclaw.JavaClawConfiguration.AGENT_MD;
import static ai.javaclaw.JavaClawConfiguration.AGENT_PRIVATE_MD;

@Component
@Order(40)
public class S4_AgentMdStep implements OnboardingProvider {

    static final String SESSION_AGENT_CONTENT = "onboarding.agent.content";

    private final Environment environment;
    private final AgentOnboardingProviders agentOnboardingProviders;
    private final AgentWorkspaceResolver agentWorkspaceResolver;

    public S4_AgentMdStep(Environment environment,
                          AgentOnboardingProviders agentOnboardingProviders,
                          AgentWorkspaceResolver agentWorkspaceResolver) {
        this.environment = environment;
        this.agentOnboardingProviders = agentOnboardingProviders;
        this.agentWorkspaceResolver = agentWorkspaceResolver;
    }

    @Override
    public String getStepId() {return "agent";}

    @Override
    public String getStepTitle() {return "AGENT.md";}

    @Override
    public String getTemplatePath() {return "onboarding/steps/S4-agent";}

    @Override
    public void prepareModel(Map<String, Object> session, Map<String, Object> model) {
        Object agentContent = session.get(SESSION_AGENT_CONTENT);
        if (agentContent == null) {
            agentContent = readFile(session, AGENT_PRIVATE_MD);
            if (agentContent == null) agentContent = readFile(session, AGENT_MD);
            if (agentContent == null) agentContent = "";
        }
        model.put("agentContent", agentContent);
    }

    private String readFile(Map<String, Object> session, String name) {
        try {
            return resolveAgentWorkspace(session).createRelative(name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String processStep(Map<String, String> formParams, Map<String, Object> session) {
        String agentContent = formParams.getOrDefault("agentContent", "");
        if (agentContent.isBlank()) {
            return "The AGENT.md instructions cannot be empty.";
        }
        session.put(SESSION_AGENT_CONTENT, agentContent);
        return null;
    }

    @Override
    public void saveConfiguration(Map<String, Object> session, ConfigurationManager configurationManager) {
        String agentContent = (String) session.getOrDefault(SESSION_AGENT_CONTENT, "");
        if (agentContent.isBlank()) return;
        try {
            Resource agentWorkspace = resolveAgentWorkspace(session);
            Files.writeString(
                    agentWorkspace.createRelative(AGENT_PRIVATE_MD).getFilePath(),
                    agentContent,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to write AGENT.private.md", e);
        }
    }

    private Resource resolveAgentWorkspace(Map<String, Object> session) throws IOException {
        String agentId = (String) session.getOrDefault(S2_ProviderStep.SESSION_AGENT_ID, environment.getProperty("agent.agents.default", "default"));
        String configuredWorkspace = environment.getProperty(S2_ProviderStep.runtimeAgentKey(agentId, "workspace"), "");
        Path workspacePath = agentWorkspaceResolver.initializeWorkspace(
                agentWorkspaceResolver.resolveWorkspacePath(configuredWorkspace, agentId)
        );
        return agentWorkspaceResolver.asResource(workspacePath);
    }
}
