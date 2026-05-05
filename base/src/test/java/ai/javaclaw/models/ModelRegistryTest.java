package ai.javaclaw.models;

import ai.javaclaw.agent.AgentRegistry;
import ai.javaclaw.agent.ConfiguredAgent;
import ai.javaclaw.configuration.ConfigurationManager;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRegistryTest {

    @Test
    void loadsRuntimeDefaultModelFromConfiguration() throws IOException {
        Path tempDir = Files.createTempDirectory("model-registry-test");
        MockEnvironment environment = new MockEnvironment().withProperty("spring.allConfig.location", "file:" + tempDir + "/");
        ConfigurationManager configurationManager = new ConfigurationManager(environment, event -> {});

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("agent.agents.default", "openai-main");
        props.put("agent.agents.items.openai-main.provider", "openai");
        props.put("agent.agents.items.openai-main.model", "gpt-5.4");
        props.put("agent.agents.items.openai-main.api-key", "secret");
        props.put("agent.agents.items.openai-main.workspace", "/tmp/openai-main");
        configurationManager.updateProperties(props);

        AgentRegistry modelRegistry = new AgentRegistry(configurationManager);

        ConfiguredAgent model = modelRegistry.getDefaultAgent().orElseThrow();
        assertEquals("openai-main", model.id());
        assertEquals("openai", model.provider());
        assertEquals("gpt-5.4", model.model());
        assertEquals("", model.baseUrl());
        assertEquals("/tmp/openai-main", model.workspacePath());
    }

    @Test
    void ignoresDisabledModels() throws IOException {
        Path tempDir = Files.createTempDirectory("model-registry-disabled-test");
        MockEnvironment environment = new MockEnvironment().withProperty("spring.allConfig.location", "file:" + tempDir + "/");
        ConfigurationManager configurationManager = new ConfigurationManager(environment, event -> {});

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("agent.agents.default", "openai-main");
        props.put("agent.agents.items.openai-main.provider", "openai");
        props.put("agent.agents.items.openai-main.model", "gpt-5.4");
        props.put("agent.agents.items.openai-main.enabled", false);
        configurationManager.updateProperties(props);

        AgentRegistry modelRegistry = new AgentRegistry(configurationManager);

        assertTrue(modelRegistry.getDefaultAgent().isEmpty());
    }

    @Test
    void fallsBackToFirstAgentWhenDefaultKeyIsMissing() throws IOException {
        Path tempDir = Files.createTempDirectory("agent-registry-missing-default-test");
        MockEnvironment environment = new MockEnvironment().withProperty("spring.allConfig.location", "file:" + tempDir + "/");
        ConfigurationManager configurationManager = new ConfigurationManager(environment, event -> {});

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("agent.agents.items.openai-main.provider", "openai");
        props.put("agent.agents.items.openai-main.model", "gpt-5.4");
        props.put("agent.agents.items.openai-main.workspace", "/tmp/openai-main");
        configurationManager.updateProperties(props);

        AgentRegistry modelRegistry = new AgentRegistry(configurationManager);

        assertFalse(modelRegistry.getAgents().isEmpty());
        assertEquals("openai-main", modelRegistry.getDefaultAgentId());
        assertEquals("openai-main", modelRegistry.getDefaultAgent().orElseThrow().id());
    }
}
