package ai.agentrunr.onboarding.steps;

import ai.agentrunr.configuration.ConfigurationManager;
import ai.agentrunr.onboarding.OnboardingProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;

@Component
@Order(40)
public class S4_AgentMdStep implements OnboardingProvider {

    static final String SESSION_AGENT_CONTENT = "onboarding.agent.content";

    private final Resource agentWorkspace;

    public S4_AgentMdStep(@Value("${agent.workspace}") Resource agentWorkspace) {
        this.agentWorkspace = agentWorkspace;
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
            agentContent = readFile("AGENT.md");
            if (agentContent == null) agentContent = readFile("AGENT-ORIGINAL.md");
            if (agentContent == null) agentContent = "";
        }
        model.put("agentContent", agentContent);
    }

    private String readFile(String name) {
        try {
            return agentWorkspace.createRelative(name).getContentAsString(StandardCharsets.UTF_8);
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
            Files.writeString(
                    agentWorkspace.createRelative("AGENT.md").getFilePath(),
                    agentContent,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to write AGENT.md", e);
        }
    }
}
