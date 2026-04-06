package ai.javaclaw.agents;

public final class AgentConversationId {

    private static final String SEPARATOR = "::";

    private AgentConversationId() {
    }

    public static String scoped(String agentId, String conversationId) {
        if (agentId == null || agentId.isBlank()) {
            return conversationId;
        }
        return agentId + SEPARATOR + conversationId;
    }

    public static boolean isScoped(String conversationId) {
        return conversationId != null && conversationId.contains(SEPARATOR);
    }

    public static String agentId(String conversationId) {
        if (!isScoped(conversationId)) {
            return null;
        }
        return conversationId.substring(0, conversationId.indexOf(SEPARATOR));
    }

    public static String rawConversationId(String conversationId) {
        if (!isScoped(conversationId)) {
            return conversationId;
        }
        return conversationId.substring(conversationId.indexOf(SEPARATOR) + SEPARATOR.length());
    }
}
