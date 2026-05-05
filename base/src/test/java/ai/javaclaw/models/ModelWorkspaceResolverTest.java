package ai.javaclaw.models;

import ai.javaclaw.agent.AgentWorkspaceResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ModelWorkspaceResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void initializeWorkspaceCopiesPromptFilesAndSkills() throws IOException {
        Path rootWorkspace = tempDir.resolve("workspace");
        Files.createDirectories(rootWorkspace.resolve("skills").resolve("skill-creator"));
        Files.writeString(rootWorkspace.resolve("AGENT.md"), "root agent");
        Files.writeString(rootWorkspace.resolve("AGENT.private.md"), "private agent");
        Files.writeString(rootWorkspace.resolve("INFO.md"), "root info");
        Files.writeString(rootWorkspace.resolve("skills").resolve("skill-creator").resolve("SKILL.md"), "skill body");

        AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(
                new DefaultResourceLoader(),
                new MockEnvironment().withProperty("agent.workspace", "file:" + rootWorkspace + "/")
        );

        Path modelWorkspace = resolver.initializeWorkspace(rootWorkspace.resolve("agents").resolve("openai-main"));

        assertThat(modelWorkspace.resolve("AGENT.md")).hasContent("root agent");
        assertThat(modelWorkspace.resolve("AGENT.private.md")).hasContent("private agent");
        assertThat(modelWorkspace.resolve("INFO.md")).hasContent("root info");
        assertThat(modelWorkspace.resolve("skills").resolve("skill-creator").resolve("SKILL.md")).hasContent("skill body");
        assertThat(modelWorkspace.resolve("context")).isDirectory();
    }
}
