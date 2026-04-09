package ai.javaclaw.providers;

import ai.javaclaw.agents.AgentChatModelFactory;
import ai.javaclaw.onboarding.AgentOnboardingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProviderWiringTest {

    @Autowired
    Set<AgentOnboardingProvider> onboardingProviders;

    @Autowired
    Set<AgentChatModelFactory> chatModelFactories;

    @Test
    void everyOnboardingProviderHasAMatchingChatModelFactory() {
        Set<String> providerIds = onboardingProviders.stream()
                .map(AgentOnboardingProvider::getId)
                .map(id -> id.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        Set<String> factoryIds = chatModelFactories.stream()
                .map(AgentChatModelFactory::providerId)
                .map(id -> id.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        assertThat(factoryIds).containsAll(providerIds);
    }
}

