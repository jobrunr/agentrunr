package ai.javaclaw.tools.search;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "javaclaw.tools.dynamic-discovery")
public record DynamicToolDiscoveryProperties(
        boolean enabled,
        Integer maxResults,
        Float luceneMinScoreThreshold
) {

    public DynamicToolDiscoveryProperties {
        if (maxResults == null) maxResults = 8;
        if (luceneMinScoreThreshold == null) luceneMinScoreThreshold = 0.25f;
    }
}

