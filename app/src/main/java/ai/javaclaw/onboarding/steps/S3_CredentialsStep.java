package ai.javaclaw.onboarding.steps;

import ai.javaclaw.onboarding.AgentOnboardingProvider;
import ai.javaclaw.onboarding.AgentOnboardingProvider.SystemWideToken;
import ai.javaclaw.onboarding.AgentOnboardingProviders;
import ai.javaclaw.onboarding.OnboardingProvider;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(30)
public class S3_CredentialsStep implements OnboardingProvider {

    private final Environment env;
    private final AgentOnboardingProviders agentOnboardingProviders;

    public S3_CredentialsStep(Environment env, AgentOnboardingProviders agentOnboardingProviders) {
        this.env = env;
        this.agentOnboardingProviders = agentOnboardingProviders;
    }

    @Override
    public String getStepId() {return "credentials";}

    @Override
    public String getStepTitle() {return "Credentials";}

    @Override
    public String getTemplatePath() {return "onboarding/steps/S3-credentials";}

    @Override
    public void prepareModel(Map<String, Object> session, Map<String, Object> model) {
        AgentOnboardingProvider provider = resolveProvider(session);
        if (provider == null) return;

        CredentialsFormState formState = buildFormState(session, provider);
        model.put("selectedProvider", provider.getId());
        model.put("agentId", formState.agentId());
        model.put("providerLabel", provider.getLabel());
        model.put("providerApiPropertyKey", formState.apiKeyPropertyKey());
        model.put("chatModelPropertyKey", formState.modelPropertyKey());
        model.put("baseUrlPropertyKey", formState.baseUrlPropertyKey());
        model.put("baseUrl", formState.baseUrl());
        model.put("requiresApiKey", provider.requiresApiKey());
        model.put("apiKey", formState.apiKey());
        model.put("model", formState.model());
        provider.systemWideToken().ifPresent(t -> model.put("systemWideTokenName", t.name()));
    }

    @Override
    public String processStep(Map<String, String> formParams, Map<String, Object> session) {
        AgentOnboardingProvider provider = resolveProvider(session);
        if (provider == null) {
            return "Provider selection is missing. Please go back and select a provider.";
        }

        String model = formParams.getOrDefault("model", "").trim();
        String apiKey = formParams.getOrDefault("apiKey", "").trim();

        if (model.isBlank()) {
            return "Enter a model to continue.";
        }

        if ("true".equals(formParams.get("useSystemToken"))) {
            SystemWideToken sysToken = provider.systemWideToken().orElse(null);
            if (sysToken == null) {
                return "System token is no longer available. Please enter your API key manually.";
            }
            apiKey = sysToken.token();
        }

        if (provider.requiresApiKey() && apiKey.isBlank()) {
            return "Enter an API key to continue.";
        }

        session.put(S2_ProviderStep.SESSION_MODEL, model);
        session.put(S2_ProviderStep.SESSION_API_KEY, apiKey);
        return null;
    }

    private AgentOnboardingProvider resolveProvider(Map<String, Object> session) {
        String providerId = (String) session.getOrDefault(S2_ProviderStep.SESSION_PROVIDER, existingProviderId());
        return agentOnboardingProviders.findById(providerId).orElse(null);
    }

    private CredentialsFormState buildFormState(Map<String, Object> session, AgentOnboardingProvider provider) {
        String agentId = currentAgentId(session);
        String persistedModel = existingModel(provider, agentId);
        String persistedApiKey = existingApiKey(provider, agentId);
        String currentModel = (String) session.get(S2_ProviderStep.SESSION_MODEL);

        return new CredentialsFormState(
                agentId,
                modelPropertyKey(provider, agentId),
                apiKeyPropertyKey(provider, agentId),
                baseUrlPropertyKey(provider, agentId),
                provider.runtimeModelProperties().get("base-url"),
                (String) session.getOrDefault(S2_ProviderStep.SESSION_API_KEY, persistedApiKey),
                currentModel != null && !currentModel.isBlank() ? currentModel : (!persistedModel.isBlank() ? persistedModel : provider.defaultModel())
        );
    }

    private String existingModel(AgentOnboardingProvider provider, String agentId) {
        return env.getProperty(S2_ProviderStep.runtimeAgentKey(agentId, "model"), "");
    }

    private String existingApiKey(AgentOnboardingProvider provider, String agentId) {
        return env.getProperty(S2_ProviderStep.runtimeAgentKey(agentId, "api-key"), "");
    }

    private String modelPropertyKey(AgentOnboardingProvider provider, String agentId) {
        return S2_ProviderStep.runtimeAgentKey(agentId, "model");
    }

    private String apiKeyPropertyKey(AgentOnboardingProvider provider, String agentId) {
        return S2_ProviderStep.runtimeAgentKey(agentId, "api-key");
    }

    private String baseUrlPropertyKey(AgentOnboardingProvider provider, String agentId) {
        if (!provider.runtimeModelProperties().containsKey("base-url")) {
            return null;
        }
        return S2_ProviderStep.runtimeAgentKey(agentId, "base-url");
    }

    private String existingProviderId() {
        String runtimeProvider = env.getProperty(S2_ProviderStep.runtimeAgentKey(currentAgentId(Map.of()), "provider"), "");
        if (!runtimeProvider.isBlank()) {
            return runtimeProvider;
        }
        return env.getProperty("spring.ai.model.chat", "");
    }

    private String currentAgentId(Map<String, Object> session) {
        Object sessionAgentId = session.get(S2_ProviderStep.SESSION_AGENT_ID);
        if (sessionAgentId instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        return env.getProperty("agent.agents.default", "default");
    }

    private record CredentialsFormState(
            String agentId,
            String modelPropertyKey,
            String apiKeyPropertyKey,
            String baseUrlPropertyKey,
            Object baseUrl,
            String apiKey,
            String model
    ) {}
}
