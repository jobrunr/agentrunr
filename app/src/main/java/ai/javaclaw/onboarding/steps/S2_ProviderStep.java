package ai.javaclaw.onboarding.steps;

import ai.javaclaw.configuration.ConfigurationManager;
import ai.javaclaw.agent.AgentWorkspaceResolver;
import ai.javaclaw.onboarding.AgentOnboardingProvider;
import ai.javaclaw.onboarding.AgentOnboardingProviders;
import ai.javaclaw.onboarding.OnboardingProvider;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(20)
public class S2_ProviderStep implements OnboardingProvider {

    static final String SESSION_PROVIDER = "onboarding.provider";
    static final String SESSION_AGENT_ID = "onboarding.agentId";
    static final String SESSION_MODEL = "onboarding.model";
    static final String SESSION_API_KEY = "onboarding.apiKey";

    private final AgentOnboardingProviders agentOnboardingProviders;
    private final AgentWorkspaceResolver agentWorkspaceResolver;
    private final Environment env;

    public S2_ProviderStep(AgentOnboardingProviders agentOnboardingProviders, AgentWorkspaceResolver agentWorkspaceResolver, Environment env) {
        this.agentOnboardingProviders = agentOnboardingProviders;
        this.agentWorkspaceResolver = agentWorkspaceResolver;
        this.env = env;
    }

    @Override
    public String getStepId() {return "provider";}

    @Override
    public String getStepTitle() {return "Provider";}

    @Override
    public String getTemplatePath() {return "onboarding/steps/S2-provider";}

    @Override
    public void prepareModel(Map<String, Object> session, Map<String, Object> model) {
        model.put("providers", agentOnboardingProviders.getAll());
        model.put("selectedProvider", session.getOrDefault(SESSION_PROVIDER, existingProviderId()));
        model.put("agentId", session.getOrDefault(SESSION_AGENT_ID, existingAgentId()));
    }

    @Override
    public String processStep(Map<String, String> formParams, Map<String, Object> session) {
        String providerId = formParams.get("provider");
        String agentId = normalizeAgentId(formParams.get("agentId"));
        if (providerId == null || providerId.isBlank()) {
            return "Choose one of the supported providers to continue.";
        }
        if (agentId == null || agentId.isBlank()) {
            return "Enter a unique agent id to continue.";
        }
        if (!agentId.matches("[a-zA-Z0-9][a-zA-Z0-9_-]*")) {
            return "Agent id may contain only letters, numbers, dashes, and underscores.";
        }
        AgentOnboardingProvider agentOnboardingProvider = agentOnboardingProviders.getById(providerId);
        // Clear downstream session state when provider changes
        String currentProvider = (String) session.get(SESSION_PROVIDER);
        if (!agentOnboardingProvider.getId().equals(currentProvider)) {
            session.remove(SESSION_MODEL);
            session.remove(SESSION_API_KEY);
        }
        session.put(SESSION_PROVIDER, agentOnboardingProvider.getId());
        session.put(SESSION_AGENT_ID, agentId);
        return null;
    }

    @Override
    public void saveConfiguration(Map<String, Object> session, ConfigurationManager configurationManager) throws IOException {
        String providerId = (String) session.get(SESSION_PROVIDER);
        String agentId = normalizeAgentId((String) session.get(SESSION_AGENT_ID));
        String model = (String) session.get(SESSION_MODEL);
        String apiKey = (String) session.getOrDefault(SESSION_API_KEY, "");

        AgentOnboardingProvider agentOnboardingProvider = agentOnboardingProviders.getById(providerId);
        Map<String, Object> props = new LinkedHashMap<>();
        saveRuntimeAgent(props, agentOnboardingProvider, agentId, model, apiKey);
        clearSpringAiProviderConfig(props);
        configurationManager.updateProperties(props);
    }

    private String existingProviderId() {
        String runtimeProvider = env.getProperty(runtimeAgentKey(existingAgentId(), "provider"), "");
        if (!runtimeProvider.isBlank()) {
            return runtimeProvider;
        }
        return env.getProperty("spring.ai.model.chat", "");
    }

    private String existingAgentId() {
        return env.getProperty("agent.agents.default", "default");
    }

    private void saveRuntimeAgent(Map<String, Object> props, AgentOnboardingProvider provider, String agentId, String model, String apiKey) {
        props.put("agent.agents.default", agentId);
        props.put(runtimeAgentKey(agentId, "enabled"), true);
        props.put(runtimeAgentKey(agentId, "provider"), provider.getId());
        props.put(runtimeAgentKey(agentId, "model"), model);
        props.put(runtimeAgentKey(agentId, "api-key"), apiKey);
        props.put(runtimeAgentKey(agentId, "workspace"), existingWorkspacePath(agentId));
        provider.runtimeModelProperties().forEach((key, value) -> props.put(runtimeAgentKey(agentId, key), value));
    }

    private String existingWorkspacePath(String agentId) {
        String configuredWorkspace = env.getProperty(runtimeAgentKey(agentId, "workspace"), "");
        if (!configuredWorkspace.isBlank()) {
            return configuredWorkspace;
        }
        return agentWorkspaceResolver.defaultWorkspacePath(agentId).toString();
    }

    private void clearSpringAiProviderConfig(Map<String, Object> props) {
        props.put("spring.ai.model.chat", null);
        props.put("spring.ai.openai.api-key", null);
        props.put("spring.ai.openai.base-url", null);
        props.put("spring.ai.openai.chat.options.model", null);
    }

    private void clearRuntimeDefaultAgent(Map<String, Object> props) {
        props.put("agent.agents.default", null);
    }

    static String runtimeAgentKey(String agentId, String suffix) {
        return "agent.agents.items." + agentId + "." + suffix;
    }

    private static String normalizeAgentId(String value) {
        return value == null ? null : value.trim();
    }
}
