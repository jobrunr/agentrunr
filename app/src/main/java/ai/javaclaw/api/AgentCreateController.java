package ai.javaclaw.api;

import ai.javaclaw.agent.AgentRegistry;
import ai.javaclaw.agent.AgentWorkspaceResolver;
import ai.javaclaw.agent.ConfiguredAgent;
import ai.javaclaw.configuration.ConfigurationManager;
import ai.javaclaw.onboarding.AgentOnboardingProvider;
import ai.javaclaw.onboarding.AgentOnboardingProviders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Controller
public class AgentCreateController {

    private final AgentRegistry agentRegistry;
    private final AgentOnboardingProviders agentOnboardingProviders;
    private final ConfigurationManager configurationManager;
    private final AgentWorkspaceResolver agentWorkspaceResolver;

    public AgentCreateController(AgentRegistry agentRegistry,
                                 AgentOnboardingProviders agentOnboardingProviders,
                                 ConfigurationManager configurationManager,
                                 AgentWorkspaceResolver agentWorkspaceResolver) {
        this.agentRegistry = agentRegistry;
        this.agentOnboardingProviders = agentOnboardingProviders;
        this.configurationManager = configurationManager;
        this.agentWorkspaceResolver = agentWorkspaceResolver;
    }

    @GetMapping("/agents/new")
    public String newAgent(Model model,
                           @RequestParam(required = false) String provider,
                           @RequestParam(required = false) String agentId,
                           @RequestParam(required = false) String modelName,
                           @RequestParam(required = false) String apiKey,
                           @RequestParam(required = false) String baseUrl,
                           @RequestParam(required = false, defaultValue = "false") boolean setDefault,
                           @RequestParam(required = false) String error) {
        model.addAttribute("providers", agentOnboardingProviders.getAll());
        model.addAttribute("selectedProvider", provider == null ? "" : provider.trim());
        model.addAttribute("agentId", agentId == null ? "" : agentId.trim());
        model.addAttribute("modelName", modelName == null ? "" : modelName.trim());
        model.addAttribute("apiKey", apiKey == null ? "" : apiKey.trim());
        model.addAttribute("baseUrl", baseUrl == null ? "" : baseUrl.trim());
        model.addAttribute("setDefault", setDefault);
        model.addAttribute("error", error);
        model.addAttribute("editing", false);
        model.addAttribute("pageTitle", "Add Agent");
        model.addAttribute("pageSubtitle", "Create a new runtime agent without running the onboarding wizard.");
        model.addAttribute("formAction", "/agents/new");
        model.addAttribute("submitLabel", "Create Agent");
        return "agents-new";
    }

    @GetMapping("/agents/{agentId}/edit")
    public String editAgent(@PathVariable String agentId,
                            Model model,
                            @RequestParam(required = false) String error) {
        ConfiguredAgent configuredAgent = agentRegistry.findAgent(agentId).orElse(null);
        if (configuredAgent == null) {
            return "redirect:/agents";
        }

        model.addAttribute("providers", agentOnboardingProviders.getAll());
        model.addAttribute("selectedProvider", configuredAgent.provider());
        model.addAttribute("agentId", configuredAgent.id());
        model.addAttribute("modelName", configuredAgent.model());
        model.addAttribute("apiKey", "");
        model.addAttribute("baseUrl", configuredAgent.baseUrl() == null ? "" : configuredAgent.baseUrl());
        model.addAttribute("setDefault", configuredAgent.id().equals(agentRegistry.getDefaultAgentId()));
        model.addAttribute("error", error);
        model.addAttribute("editing", true);
        model.addAttribute("pageTitle", "Edit Agent");
        model.addAttribute("pageSubtitle", "Update an existing runtime agent configuration.");
        model.addAttribute("formAction", "/agents/" + url(configuredAgent.id()) + "/edit");
        model.addAttribute("submitLabel", "Save Changes");
        return "agents-new";
    }

    @PostMapping("/agents/new")
    public String createAgent(@RequestParam Map<String, String> formParams, HttpServletRequest request) throws IOException {
        String providerId = trim(formParams.get("provider"));
        String agentId = trim(formParams.get("agentId"));
        String modelName = trim(formParams.get("model"));
        String apiKey = trim(formParams.get("apiKey"));
        String baseUrl = trim(formParams.get("baseUrl"));
        boolean setDefault = "on".equalsIgnoreCase(formParams.getOrDefault("setDefault", ""));

        if (providerId.isBlank()) {
            return redirectBack("Choose one of the supported providers to continue.", providerId, agentId, modelName, apiKey, baseUrl, setDefault);
        }
        if (agentId.isBlank()) {
            return redirectBack("Enter a unique agent id to continue.", providerId, agentId, modelName, apiKey, baseUrl, setDefault);
        }
        if (!agentId.matches("[a-zA-Z0-9][a-zA-Z0-9_-]*")) {
            return redirectBack("Agent id may contain only letters, numbers, dashes, and underscores.", providerId, agentId, modelName, apiKey, baseUrl, setDefault);
        }

        Optional<AgentOnboardingProvider> providerOpt = agentOnboardingProviders.findById(providerId);
        if (providerOpt.isEmpty()) {
            return redirectBack("Selected provider is no longer available.", providerId, agentId, modelName, apiKey, baseUrl, setDefault);
        }
        AgentOnboardingProvider provider = providerOpt.get();

        if (modelName.isBlank()) {
            return redirectBack("Enter a model to continue.", providerId, agentId, modelName, apiKey, baseUrl, setDefault);
        }
        if (provider.requiresApiKey() && apiKey.isBlank()) {
            return redirectBack("Enter an API key to continue.", providerId, agentId, modelName, apiKey, baseUrl, setDefault);
        }

        if (agentIdAlreadyConfigured(agentId) || agentRegistry.findAgent(agentId).isPresent()) {
            return redirectBack("Agent id is already configured. Choose a different id.", providerId, agentId, modelName, apiKey, baseUrl, setDefault);
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put(runtimeAgentKey(agentId, "enabled"), true);
        props.put(runtimeAgentKey(agentId, "provider"), provider.getId());
        props.put(runtimeAgentKey(agentId, "model"), modelName);
        props.put(runtimeAgentKey(agentId, "api-key"), apiKey);

        String workspacePath = agentWorkspaceResolver.defaultWorkspacePath(agentId).toString();
        props.put(runtimeAgentKey(agentId, "workspace"), workspacePath);

        provider.runtimeModelProperties().forEach((key, value) -> {
            if ("base-url".equals(key) && !baseUrl.isBlank()) {
                props.put(runtimeAgentKey(agentId, key), baseUrl);
            } else {
                props.put(runtimeAgentKey(agentId, key), value);
            }
        });
        if (!baseUrl.isBlank() && !provider.runtimeModelProperties().containsKey("base-url")) {
            props.put(runtimeAgentKey(agentId, "base-url"), baseUrl);
        }

        if (setDefault) {
            props.put("agent.agents.default", agentId);
        }

        configurationManager.updateProperties(props);

        agentWorkspaceResolver.initializeWorkspace(Path.of(workspacePath));

        return "redirect:/agents";
    }

    @PostMapping("/agents/{agentId}/edit")
    public String updateAgent(@PathVariable String agentId, @RequestParam Map<String, String> formParams) throws IOException {
        ConfiguredAgent configuredAgent = agentRegistry.findAgent(agentId).orElse(null);
        if (configuredAgent == null) {
            return "redirect:/agents";
        }

        String providerId = trim(formParams.get("provider"));
        String modelName = trim(formParams.get("model"));
        String apiKey = trim(formParams.get("apiKey"));
        String baseUrl = trim(formParams.get("baseUrl"));
        boolean setDefault = "on".equalsIgnoreCase(formParams.getOrDefault("setDefault", ""));

        if (providerId.isBlank()) {
            return "redirect:/agents/" + url(agentId) + "/edit?error=" + url("Choose one of the supported providers to continue.");
        }

        Optional<AgentOnboardingProvider> providerOpt = agentOnboardingProviders.findById(providerId);
        if (providerOpt.isEmpty()) {
            return "redirect:/agents/" + url(agentId) + "/edit?error=" + url("Selected provider is no longer available.");
        }
        AgentOnboardingProvider provider = providerOpt.get();

        if (modelName.isBlank()) {
            return "redirect:/agents/" + url(agentId) + "/edit?error=" + url("Enter a model to continue.");
        }

        String effectiveApiKey = apiKey.isBlank() ? (configuredAgent.apiKey() == null ? "" : configuredAgent.apiKey()) : apiKey;
        if (provider.requiresApiKey() && effectiveApiKey.isBlank()) {
            return "redirect:/agents/" + url(agentId) + "/edit?error=" + url("Enter an API key to continue.");
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put(runtimeAgentKey(agentId, "enabled"), true);
        props.put(runtimeAgentKey(agentId, "provider"), provider.getId());
        props.put(runtimeAgentKey(agentId, "model"), modelName);
        props.put(runtimeAgentKey(agentId, "api-key"), effectiveApiKey);

        String workspacePath = configuredAgent.workspacePath();
        if (workspacePath == null || workspacePath.isBlank()) {
            workspacePath = agentWorkspaceResolver.defaultWorkspacePath(agentId).toString();
        }
        props.put(runtimeAgentKey(agentId, "workspace"), workspacePath);

        String effectiveBaseUrl = baseUrl.isBlank() ? (configuredAgent.baseUrl() == null ? "" : configuredAgent.baseUrl()) : baseUrl;
        if (!effectiveBaseUrl.isBlank()) {
            props.put(runtimeAgentKey(agentId, "base-url"), effectiveBaseUrl);
        }

        provider.runtimeModelProperties().forEach((key, value) -> {
            if ("base-url".equals(key)) {
                return;
            }
            props.put(runtimeAgentKey(agentId, key), value);
        });

        if (setDefault) {
            props.put("agent.agents.default", agentId);
        }

        configurationManager.updateProperties(props);
        return "redirect:/agents";
    }

    private boolean agentIdAlreadyConfigured(String agentId) throws IOException {
        Map<String, Object> config = configurationManager.readApplicationYaml();
        Object agentSection = config.get("agent");
        if (!(agentSection instanceof Map<?, ?> agentMap)) return false;
        Object agentsSection = ((Map<?, ?>) agentMap).get("agents");
        if (!(agentsSection instanceof Map<?, ?> agentsMap)) return false;
        Object itemsSection = ((Map<?, ?>) agentsMap).get("items");
        if (!(itemsSection instanceof Map<?, ?> itemsMap)) return false;
        return itemsMap.containsKey(agentId);
    }

    private static String runtimeAgentKey(String agentId, String suffix) {
        return "agent.agents.items." + agentId + "." + suffix;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String redirectBack(String error,
                                      String provider,
                                      String agentId,
                                      String modelName,
                                      String apiKey,
                                      String baseUrl,
                                      boolean setDefault) {
        return "redirect:/agents/new?error=" + url(error)
                + "&provider=" + url(provider)
                + "&agentId=" + url(agentId)
                + "&modelName=" + url(modelName)
                + "&apiKey=" + url(apiKey)
                + "&baseUrl=" + url(baseUrl)
                + "&setDefault=" + setDefault;
    }

    private static String url(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
