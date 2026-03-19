package ai.javaclaw.tasks;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

@Component
public class FileSystemTaskRepository implements TaskRepository {

    private final Path taskDir;

    public FileSystemTaskRepository(@Value("${agent.workspace:Unknown}") Resource workspaceDir) throws IOException {
        this.taskDir = workspaceDir.getFilePath().resolve("tasks");
    }

    @Override
    public Task save(Task task) {
        Path path = ofNullable(task.getId()).map(Path::of).orElseGet(() -> buildTaskPath(task));
        writeTaskFile(path, task);
        return new Task(path.toAbsolutePath().toString(), task.getName(), task.getCreatedAt(), task.getStatus(), task.getDescription());
    }

    @Override
    public Task getTaskById(String id) {
        try {
            Path path = Path.of(id);
            List<String> lines = Files.readAllLines(path);
            String name = lines.get(1).substring("task: ".length());
            Instant createdAt = Instant.parse(lines.get(2).substring("createdAt: ".length()));
            Task.Status status = Task.Status.valueOf(lines.get(3).substring("status: ".length()));
            StringBuilder desc = new StringBuilder();
            for (int i = 4; i < lines.size(); i++) {
                if (lines.get(i).equals("---")) break;
                if (i == 4) desc.append(lines.get(i).substring("description: ".length()));
                else desc.append(System.lineSeparator()).append(lines.get(i));
            }
            return new Task(id, name, createdAt, status, desc.toString());
        } catch (IOException e) {
            throw new TaskNotFoundException(id, e);
        }
    }

    @Override
    public List<Task> getTasks(LocalDate date, Task.Status status) {
        Path dir = taskDir.resolve(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        if (!Files.exists(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .map(p -> getTaskById(p.toAbsolutePath().toString()))
                    .filter(t -> status == null || t.getStatus() == status)
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list tasks for " + date, e);
        }
    }

    @Override
    public RecurringTask save(RecurringTask recurringTask) {
        String validName = sanitizeName(recurringTask.getName());
        Path dir = ensureDirectory(taskDir.resolve("recurring"));
        Path path = dir.resolve(validName + ".md");
        writeRecurringTaskFile(path, recurringTask);
        return new RecurringTask(path.toAbsolutePath().toString(), recurringTask.getName(), recurringTask.getDescription());
    }

    @Override
    public RecurringTask getRecurringTaskById(String id) {
        try {
            Path path = Path.of(id);
            List<String> lines = Files.readAllLines(path);
            String name = lines.get(1).substring("task: ".length());
            StringBuilder desc = new StringBuilder();
            for (int i = 2; i < lines.size(); i++) {
                if (lines.get(i).equals("---")) break;
                if (i == 2) desc.append(lines.get(i).substring("description: ".length()));
                else desc.append(System.lineSeparator()).append(lines.get(i));
            }
            return new RecurringTask(id, name, desc.toString());
        } catch (IOException e) {
            throw new TaskNotFoundException(id, e);
        }
    }

    @Override
    public List<RecurringTask> getAllRecurringTasks() {
        try {
            Path dir = ensureDirectory(taskDir.resolve("recurring"));
            try(Stream<Path> recurringTasks = Files.list(dir)) {
                return recurringTasks
                        .map(p -> p.toAbsolutePath().toString())
                        .map(this::getRecurringTaskById)
                        .toList();
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not list all recurring tasks", e);
        }
    }


    @Override
    public void deleteRecurringTask(String id) {
        try {
            Files.deleteIfExists(Path.of(id));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete recurring task file: " + id, e);
        }
    }

    // --- private helpers ---

    private Path buildTaskPath(Task task) {
        LocalDateTime dateTime = task.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime();
        String dateStr = dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String timeStr = dateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HHmmss"));
        String safeName = sanitizeName(task.getName());
        Path dir = ensureDirectory(taskDir.resolve(dateStr));
        return dir.resolve(String.format("%s-%s.md", timeStr, safeName));
    }

    private void writeTaskFile(Path path, Task task) {
        StringBuilder sb = new StringBuilder();
        sb.append("---").append(System.lineSeparator());
        sb.append("task: ").append(task.getName()).append(System.lineSeparator());
        sb.append("createdAt: ").append(task.getCreatedAt()).append(System.lineSeparator());
        sb.append("status: ").append(task.getStatus()).append(System.lineSeparator());
        sb.append("description: ").append(task.getDescription()).append(System.lineSeparator());
        writeFile(path, sb.toString());
    }

    private void writeRecurringTaskFile(Path path, RecurringTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("---").append(System.lineSeparator());
        sb.append("task: ").append(task.getName()).append(System.lineSeparator());
        sb.append("description: ").append(task.getDescription()).append(System.lineSeparator());
        writeFile(path, sb.toString());
    }

    private static void writeFile(Path path, String content) {
        try {
            if (!Files.exists(path)) Files.createFile(path);
            Files.writeString(path, content, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write task file: " + path, e);
        }
    }

    private static Path ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + dir, e);
        }
    }

    static String sanitizeName(String name) {
        return name
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("_{2,}", "_");
    }
}
