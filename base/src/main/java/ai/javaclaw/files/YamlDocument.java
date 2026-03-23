package ai.javaclaw.files;

import java.util.Map;

/**
 * Represents a file with an optional YAML frontmatter block and a free-form body.
 *
 * <p>Format:
 * <pre>
 * ---
 * key: value
 * multiline: first line
 * second line
 * ---
 * Body content here.
 * </pre>
 *
 * <p>Either section may be absent:
 * <ul>
 *   <li>Task {@code .md} files: frontmatter only (no closing {@code ---}, empty body).</li>
 *   <li>Chat {@code .yaml} files: body only (no frontmatter, empty map).</li>
 * </ul>
 */
public record YamlDocument(Map<String, String> frontmatter, String body) {
}
