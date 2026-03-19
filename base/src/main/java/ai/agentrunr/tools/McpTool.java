package ai.agentrunr.tools;

import ai.agentrunr.configuration.ConfigurationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Allows the agent to register new MCP servers at runtime by persisting them
 * into application.yaml via ConfigurationManager. Changes take effect on restart.
 */
public class McpTool {

    private static final Logger logger = LoggerFactory.getLogger(McpTool.class);

    private final ConfigurationManager configurationManager;

    public McpTool(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    @Tool(description = """
            Adds a new streamable-HTTP MCP server to the application configuration.
            
            Parameters:
            - name: Unique identifier for the server (letters, numbers, hyphens, underscores only).
            - url: The MCP endpoint URL (e.g., http://localhost:8000/mcp).
            - headers: Optional HTTP headers in "Key: Value" format, one per line. Leave blank if not needed.
            """)
    public String addStreamableHttpMcpServer(String name, String url, String headers) {
        if (!name.matches("[a-zA-Z0-9_-]+")) {
            return "Error: Server name may only contain letters, numbers, hyphens and underscores.";
        }
        try {
            Map<String, Object> props = new LinkedHashMap<>();
            URI uri = new URI(url);
            props.put("spring.ai.mcp.client.streamable-http.connections." + name + ".url", uri.getScheme() + "://" + uri.getAuthority());
            props.put("spring.ai.mcp.client.streamable-http.connections." + name + ".endpoint", uri.getPath());
            props.put("spring.ai.mcp.client.streamable-http.connections." + name + ".url", url);
            if (headers != null && !headers.isBlank()) {
                for (String line : headers.split("\n")) {
                    line = line.trim();
                    int sep = line.indexOf(':');
                    if (sep > 0) {
                        String key = line.substring(0, sep).trim();
                        String value = line.substring(sep + 1).trim();
                        props.put("spring.ai.mcp.client.streamable-http.connections." + name + ".headers." + key, value);
                    }
                }
            }
            configurationManager.updateProperties(props);
            return "MCP server '" + name + "' (streamable-http) added successfully. Restart the application for it to take effect.";
        } catch (Exception e) {
            logger.error("Failed to add streamable-http MCP server '{}'", name, e);
            return "Error: Could not add MCP server. " + e.getMessage();
        }
    }

    @Tool(description = """
            Adds a new stdio MCP server to the application configuration.
            
            Parameters:
            - name: Unique identifier for the server (letters, numbers, hyphens, underscores only).
            - command: The executable command to launch the MCP server process
                       (e.g., "npx -y @modelcontextprotocol/server-brave-search").
            - args: Optional additional arguments, comma-separated (e.g., "--port,8080"). Leave blank if not needed.
            - env: Optional environment variables in "KEY=VALUE" format, one per line. Leave blank if not needed.
            """)
    public String addStdioMcpServer(String name, String command, String args, String env) {
        if (!name.matches("[a-zA-Z0-9_-]+")) {
            return "Error: Server name may only contain letters, numbers, hyphens and underscores.";
        }
        try {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("spring.ai.mcp.client.stdio.connections." + name + ".command", command);
            if (args != null && !args.isBlank()) {
                List<String> argList = Arrays.stream(args.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
                if (!argList.isEmpty()) {
                    props.put("spring.ai.mcp.client.stdio.connections." + name + ".args", argList);
                }
            }
            if (env != null && !env.isBlank()) {
                for (String line : env.split("\n")) {
                    line = line.trim();
                    int sep = line.indexOf('=');
                    if (sep > 0) {
                        String key = line.substring(0, sep).trim();
                        String value = line.substring(sep + 1).trim();
                        props.put("spring.ai.mcp.client.stdio.connections." + name + ".env." + key, value);
                    }
                }
            }
            configurationManager.updateProperties(props);
            return "MCP server '" + name + "' (stdio) added successfully. Restart the application for it to take effect.";
        } catch (Exception e) {
            logger.error("Failed to add stdio MCP server '{}'", name, e);
            return "Error: Could not add MCP server. " + e.getMessage();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ConfigurationManager configurationManager;

        public Builder configurationManager(ConfigurationManager configurationManager) {
            this.configurationManager = configurationManager;
            return this;
        }

        public McpTool build() {
            return new McpTool(configurationManager);
        }
    }
}
