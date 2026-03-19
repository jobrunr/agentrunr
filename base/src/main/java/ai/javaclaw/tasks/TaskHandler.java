package ai.javaclaw.tasks;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelRegistry;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TaskHandler {

    private static final Logger LOGGER = new JobRunrDashboardLogger(LoggerFactory.getLogger(TaskHandler.class));

    private final Agent agent;
    private final TaskRepository taskRepository;
    private final ChannelRegistry channelRegistry;

    public TaskHandler(Agent agent, TaskRepository taskRepository, ChannelRegistry channelRegistry) {
        this.agent = agent;
        this.taskRepository = taskRepository;
        this.channelRegistry = channelRegistry;
    }

    @Job(name = "%0", retries = 3)
    public void executeTask(String taskId) {
        Task task = taskRepository.getTaskById(taskId);

        if (!Task.Status.todo.equals(task.getStatus())) {
            throw new IllegalStateException("Cannot handle task '" + task.getName() + "' with status " + task.getStatus() + ". Only tasks that have status todo can be run");
        }

        Task inProgress = taskRepository.save(task.withStatus(Task.Status.in_progress));
        try {
            LOGGER.info("Starting task: {}", task.getName());
            String agentInput = formatTaskForAgent(inProgress);
            TaskResult result = agent.prompt(taskId, agentInput, TaskResult.class);
            taskRepository.save(inProgress.withFeedback(result.feedback()).withStatus(result.newStatus()));
            LOGGER.info("Finished task: {} with status {}", task.getName(), result.newStatus());
            notifyUser(task, result);
        } catch (Exception e) {
            taskRepository.save(inProgress.withStatus(Task.Status.todo));
            throw e;
        }
    }

    private void notifyUser(Task task, TaskResult result) {
        try {
            Channel channel = channelRegistry.getLatestChannel();
            if (channel != null) {
                String message = String.format("Task '%s' %s:\n%s",
                        task.getName(), result.newStatus(), result.feedback());
                channel.sendMessage(message);
            } else {
                LOGGER.warn("No active channel to notify about task '{}' completion", task.getName());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to send task completion notification for '{}': {}", task.getName(), e.getMessage());
        }
    }

    private String formatTaskForAgent(Task task) {
        return String.format("""
                Handle the following task and report the new status ('completed' or 'awaiting_human_input') with the feedback what was done
                Task '%s': %s
                """, task.getName(), task.getDescription());
    }

    public record TaskResult(Task.Status newStatus, String feedback) {}
}
