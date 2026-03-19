package ai.agentrunr.onboarding.steps;

import ai.agentrunr.SupportedProvider;
import ai.agentrunr.configuration.ConfigurationManager;
import ai.agentrunr.onboarding.OnboardingProvider;
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
    static final String SESSION_MODEL = "onboarding.model";
    static final String SESSION_API_KEY = "onboarding.apiKey";

    private final Environment env;

    public S2_ProviderStep(Environment env) {
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
        model.put("providers", SupportedProvider.supportedAgents());
        model.put("selectedProvider", session.getOrDefault(SESSION_PROVIDER, env.getProperty("spring.ai.model.chat", "")));
    }

    @Override
    public String processStep(Map<String, String> formParams, Map<String, Object> session) {
        String providerId = formParams.get("provider");
        if (providerId == null || providerId.isBlank()) {
            return "Choose one of the supported providers to continue.";
        }
        SupportedProvider provider = SupportedProvider.from(providerId).orElse(null);
        if (provider == null) {
            return "Choose one of the supported providers to continue.";
        }
        // Clear downstream session state when provider changes
        String currentProvider = (String) session.get(SESSION_PROVIDER);
        if (!provider.id().equals(currentProvider)) {
            session.remove(SESSION_MODEL);
            session.remove(SESSION_API_KEY);
        }
        session.put(SESSION_PROVIDER, provider.id());
        return null;
    }

    @Override
    public void saveConfiguration(Map<String, Object> session, ConfigurationManager configurationManager) throws IOException {
        String providerId = (String) session.get(SESSION_PROVIDER);
        String model = (String) session.get(SESSION_MODEL);
        String apiKey = (String) session.getOrDefault(SESSION_API_KEY, "");

        SupportedProvider provider = SupportedProvider.from(providerId).orElse(null);
        if (provider == null) return;

        Map<String, Object> props = new LinkedHashMap<>();
        provider.saveProperty(props, "chat.options.model", model);
        provider.saveProperty(props, "api-key", apiKey);
        props.put("spring.ai.model.chat", provider.id());
        configurationManager.updateProperties(props);
    }
}
