package ai.javaclaw.tasks;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RecurringTaskHandler {

    private static final Logger LOGGER = new JobRunrDashboardLogger(LoggerFactory.getLogger(RecurringTaskHandler.class));

    private final TaskManager taskManager;
    private final TaskRepository taskRepository;

    public RecurringTaskHandler(TaskManager taskManager, TaskRepository taskRepository) {
        this.taskManager = taskManager;
        this.taskRepository = taskRepository;
    }

    @Job(name = "Recurring task '%0'", retries = 3)
    public void executeTask(String recurringTaskId) {
        RecurringTask recurringTask = taskRepository.getRecurringTaskById(recurringTaskId);
        taskManager.createTaskFromRecurringTask(recurringTask);
    }
}
