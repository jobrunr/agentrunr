package ai.javaclaw.agents;

import ai.javaclaw.configuration.ConfigurationChangedEvent;
import ai.javaclaw.configuration.ConfigurationManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AgentRegistry {

    public static final String DEFAULT_AGENT_ID = "default";

    private volatile String defaultAgentId = DEFAULT_AGENT_ID;
    private volatile Map<String, ConfiguredAgent> agents = Map.of();

    public AgentRegistry(ConfigurationManager configurationManager) throws IOException {
        reload(configurationManager.readApplicationYaml());
    }

    public Optional<ConfiguredAgent> getDefaultAgent() {
        ConfiguredAgent configuredAgent = agents.get(defaultAgentId);
        if (configuredAgent != null) {
            return Optional.of(configuredAgent);
        }
        return agents.values().stream().findFirst();
    }

    public Optional<ConfiguredAgent> findAgent(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(agents.get(agentId));
    }

    public List<ConfiguredAgent> getAgents() {
        return List.copyOf(agents.values());
    }

    public String getDefaultAgentId() {
        return defaultAgentId;
    }

    @EventListener
    public void onConfigurationChanged(ConfigurationChangedEvent event) {
        reload(event.allConfig());
    }

    @SuppressWarnings("unchecked")
    private void reload(Map<String, Object> config) {
        Object agentSection = config.get("agent");
        if (!(agentSection instanceof Map<?, ?> agentMap)) {
            clear();
            return;
        }

        Object agentsSection = ((Map<String, Object>) agentMap).get("agents");
        if (!(agentsSection instanceof Map<?, ?> agentsMap)) {
            clear();
            return;
        }

        Map<String, Object> agentsConfig = (Map<String, Object>) agentsMap;
        String configuredDefaultAgentId = stringValue(agentsConfig.get("default"), "");

        Object itemsSection = agentsConfig.get("items");
        Map<String, Object> itemsConfig;
        if (itemsSection instanceof Map<?, ?> itemsMap) {
            itemsConfig = (Map<String, Object>) itemsMap;
        } else {
            itemsConfig = Map.of();
        }

        Map<String, ConfiguredAgent> loadedAgents = new LinkedHashMap<>();
        itemsConfig.forEach((id, value) -> {
            if (!(value instanceof Map<?, ?> rawAgentProperties)) {
                return;
            }

            Map<String, Object> agentProperties = (Map<String, Object>) rawAgentProperties;
            if (!booleanValue(agentProperties.get("enabled"), true)) {
                return;
            }

            String provider = stringValue(agentProperties.get("provider"), "");
            String model = stringValue(agentProperties.get("model"), "");
            String apiKey = stringValue(agentProperties.get("api-key"), "");
            String baseUrl = stringValue(agentProperties.get("base-url"), "");
            String workspacePath = stringValue(agentProperties.get("workspace"), "");
            if (provider.isBlank() || model.isBlank()) {
                return;
            }
            loadedAgents.put(id, new ConfiguredAgent(id, provider, model, apiKey, baseUrl, workspacePath));
        });

        agents = Map.copyOf(loadedAgents);
        defaultAgentId = resolveDefaultAgentId(configuredDefaultAgentId, loadedAgents);
    }

    private void clear() {
        defaultAgentId = DEFAULT_AGENT_ID;
        agents = Map.of();
    }

    private String resolveDefaultAgentId(String configuredDefaultAgentId, Map<String, ConfiguredAgent> loadedAgents) {
        if (!configuredDefaultAgentId.isBlank() && loadedAgents.containsKey(configuredDefaultAgentId)) {
            return configuredDefaultAgentId;
        }
        return loadedAgents.keySet().stream().findFirst().orElse(DEFAULT_AGENT_ID);
    }

    private static String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String stringValue = value.toString().trim();
        return stringValue.isBlank() ? defaultValue : stringValue;
    }

    private static boolean booleanValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
