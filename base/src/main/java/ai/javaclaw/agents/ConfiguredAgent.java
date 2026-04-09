package ai.javaclaw.agents;

public record ConfiguredAgent(
        String id,
        String provider,
        String model,
        String apiKey,
        String baseUrl,
        String workspacePath
) { }
