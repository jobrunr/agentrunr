package ai.javaclaw.agent.memory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Serialises and deserialises a list of Spring AI {@link Message} objects to/from
 * a YAML block-list string, for use as the body of a {@link ai.javaclaw.files.YamlDocument}.
 *
 * <p>Format (one entry per message, role is the key):
 * <pre>
 * - user: |
 *     Question text
 * - assistant: |
 *     Answer text
 * </pre>
 */
class ChatYamlSerializer {

    private static final Set<MessageType> PERSISTABLE_MESSAGES = Set.of(MessageType.USER, MessageType.ASSISTANT, MessageType.SYSTEM);

    private ChatYamlSerializer() {}

    static List<Message> deserialize(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        Yaml yaml = new Yaml();
        List<Map<String, String>> entries = yaml.load(body);
        if (entries == null) {
            return List.of();
        }
        return entries.stream()
                .map(entry -> {
                    Map.Entry<String, String> first = entry.entrySet().iterator().next();
                    return toMessage(first.getKey(), first.getValue());
                })
                .collect(Collectors.toList());
    }

    static String serialize(List<Message> messages) {
        List<Map<String, String>> entries = messages.stream()
                .filter(msg -> PERSISTABLE_MESSAGES.contains(msg.getMessageType()) && msg.getText() != null)
                .map(msg -> {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put(msg.getMessageType().getValue(), msg.getText());
                    return entry;
                })
                .collect(Collectors.toList());

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(entries);
    }

    private static Message toMessage(String role, String content) {
        return switch (role) {
            case "user" -> new UserMessage(content);
            case "assistant" -> new AssistantMessage(content);
            case "system" -> new SystemMessage(content);
            default -> throw new IllegalArgumentException("Unknown role in chat history: " + role);
        };
    }
}
