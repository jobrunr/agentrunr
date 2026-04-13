package ai.javaclaw.tools.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dynamic tool discovery configuration (Issue #49).
 *
 * When enabled, JavaClaw uses Spring AI's Tool Search Tool pattern so the model can
 * discover tools at runtime rather than receiving the full tool set up front.
 */
@ConfigurationProperties(prefix = "javaclaw.tools.dynamic-discovery")
public record DynamicToolDiscoveryProperties(
        boolean enabled,
        Integer maxResults,
        Float luceneMinScoreThreshold
) {

    public DynamicToolDiscoveryProperties {
        // Defaults keep the feature low-risk and predictable.
        if (maxResults == null) maxResults = 8;
        if (luceneMinScoreThreshold == null) luceneMinScoreThreshold = 0.25f;
    }
}

