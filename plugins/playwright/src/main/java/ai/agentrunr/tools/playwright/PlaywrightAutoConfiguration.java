package ai.agentrunr.tools.playwright;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(name = "agent.tools.playwright.enabled", havingValue = "true")
public class PlaywrightAutoConfiguration {

    @Bean(destroyMethod = "close")
    public PlaywrightBrowserTool playwrightBrowserTool(
            @Value("${agent.tools.playwright.headless:true}") boolean headless) {
        return PlaywrightBrowserTool.builder().headless(headless).build();
    }
}
