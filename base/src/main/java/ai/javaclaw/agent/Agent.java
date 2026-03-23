package ai.javaclaw.agent;

public interface Agent {

    String respondTo(String conversationId, String question);

    <T> T prompt(String conversationId, String input, Class<T> result);

}
