package ai.javaclaw.agent;

import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static ai.javaclaw.JavaClawConfiguration.AGENT_MD;
import static ai.javaclaw.JavaClawConfiguration.AGENT_PRIVATE_MD;
import static ai.javaclaw.JavaClawConfiguration.INFO_MD;

@Component
public class AgentWorkspaceResolver {

    private final ResourceLoader resourceLoader;
    private final Path rootWorkspacePath;

    public AgentWorkspaceResolver(ResourceLoader resourceLoader, Environment environment) {
        this.resourceLoader = resourceLoader;
        try {
            this.rootWorkspacePath = resourceLoader.getResource(environment.getProperty("agent.workspace", "file:./workspace/")).getFilePath();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to resolve root workspace path", e);
        }
    }

    public Path rootWorkspacePath() {
        return rootWorkspacePath;
    }

    public Path defaultWorkspacePath(String agentId) {
        return rootWorkspacePath.resolve("agents").resolve(agentId);
    }

    public Path resolveWorkspacePath(String configuredPath, String agentId) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return defaultWorkspacePath(agentId);
        }
        return Path.of(configuredPath);
    }

    public Resource rootWorkspace() {
        return asResource(rootWorkspacePath);
    }

    public Resource asResource(Path workspacePath) {
        return resourceLoader.getResource("file:" + workspacePath.toAbsolutePath() + "/");
    }

    public Path initializeWorkspace(Path workspacePath) throws IOException {
        Files.createDirectories(workspacePath);
        copyIfMissing(rootWorkspacePath.resolve(INFO_MD), workspacePath.resolve(INFO_MD));
        copyIfMissing(rootWorkspacePath.resolve(AGENT_MD), workspacePath.resolve(AGENT_MD));
        copyIfMissing(rootWorkspacePath.resolve(AGENT_PRIVATE_MD), workspacePath.resolve(AGENT_PRIVATE_MD));
        copyDirectoryContentsIfMissing(rootWorkspacePath.resolve("skills"), workspacePath.resolve("skills"));
        Files.createDirectories(workspacePath.resolve("context"));
        return workspacePath;
    }

    private static void copyIfMissing(Path source, Path target) throws IOException {
        if (!Files.exists(source) || Files.exists(target)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
    }

    private static void copyDirectoryContentsIfMissing(Path sourceDir, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        if (!Files.isDirectory(sourceDir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(sourceDir)) {
            for (Path source : paths.toList()) {
                Path relativePath = sourceDir.relativize(source);
                Path target = targetDir.resolve(relativePath);
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    copyIfMissing(source, target);
                }
            }
        }
    }
}
