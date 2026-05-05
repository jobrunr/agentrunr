package ai.javaclaw.agent;

public interface Agent {

    default String respondTo(String conversationId, String question) {
        return respondTo(null, conversationId, question);
    }

    String respondTo(String agentId, String conversationId, String question);

    default <T> T prompt(String conversationId, String input, Class<T> result) {
        return prompt(null, conversationId, input, result);
    }

    <T> T prompt(String agentId, String conversationId, String input, Class<T> result);

}
