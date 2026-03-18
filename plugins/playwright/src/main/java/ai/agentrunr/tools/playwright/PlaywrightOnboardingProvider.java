package ai.agentrunr.tools.playwright;

import ai.agentrunr.configuration.ConfigurationManager;
import ai.agentrunr.onboarding.OnboardingProvider;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@Order(55)
public class PlaywrightOnboardingProvider implements OnboardingProvider {

    static final String SESSION_ENABLED = "onboarding.playwright.enabled";

    private final Environment env;

    public PlaywrightOnboardingProvider(Environment env) {
        this.env = env;
    }

    @Override
    public boolean isOptional() { return true; }

    @Override
    public String getStepId() { return "playwright"; }

    @Override
    public String getStepTitle() { return "Browser Automation"; }

    @Override
    public String getTemplatePath() { return "onboarding/steps/playwright"; }

    @Override
    public void prepareModel(Map<String, Object> session, Map<String, Object> model) {
        String enabled = (String) session.getOrDefault(SESSION_ENABLED,
                env.getProperty("agent.tools.playwright.enabled", "false"));
        model.put("playwrightEnabled", "true".equals(enabled));
    }

    @Override
    public String processStep(Map<String, String> formParams, Map<String, Object> session) {
        String enabled = formParams.containsKey("playwrightEnabled") ? "true" : "false";
        session.put(SESSION_ENABLED, enabled);
        return null;
    }

    @Override
    public void saveConfiguration(Map<String, Object> session, ConfigurationManager configurationManager) throws IOException {
        String enabled = (String) session.getOrDefault(SESSION_ENABLED, "false");
        if ("true".equals(enabled)) {
            configurationManager.updateProperties(Map.of(
                    "agent.tools.playwright.enabled", true
            ));
        }
    }
}
