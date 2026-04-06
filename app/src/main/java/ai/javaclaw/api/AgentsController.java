package ai.javaclaw.api;

import ai.javaclaw.agents.AgentRegistry;
import ai.javaclaw.agents.AgentWorkspaceResolver;
import ai.javaclaw.agents.ConfiguredAgent;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.file.Path;
import java.util.List;

@Controller
public class AgentsController {

    private final AgentRegistry agentRegistry;
    private final AgentWorkspaceResolver agentWorkspaceResolver;

    public AgentsController(AgentRegistry agentRegistry, AgentWorkspaceResolver agentWorkspaceResolver) {
        this.agentRegistry = agentRegistry;
        this.agentWorkspaceResolver = agentWorkspaceResolver;
    }

    @GetMapping("/agents")
    public String agents(Model model) {
        List<AgentView> agents = agentRegistry.getAgents().stream()
                .map(configuredAgent -> new AgentView(
                        configuredAgent.id(),
                        configuredAgent.provider(),
                        configuredAgent.model(),
                        resolveWorkspace(configuredAgent),
                        configuredAgent.id().equals(agentRegistry.getDefaultAgentId())
                ))
                .toList();

        model.addAttribute("agents", agents);
        model.addAttribute("hasAgents", !agents.isEmpty());
        return "agents";
    }

    private String resolveWorkspace(ConfiguredAgent configuredAgent) {
        Path workspacePath = agentWorkspaceResolver.resolveWorkspacePath(configuredAgent.workspacePath(), configuredAgent.id());
        return workspacePath.toString();
    }

    public record AgentView(
            String id,
            String provider,
            String model,
            String workspace,
            boolean isDefault
    ) {
    }
}
