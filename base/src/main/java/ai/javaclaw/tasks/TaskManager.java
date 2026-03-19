package ai.javaclaw.tasks;

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.Paging;
import org.jobrunr.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class TaskManager {

    private static final Logger log = LoggerFactory.getLogger(TaskManager.class);
    private final JobScheduler jobScheduler;
    private final StorageProvider storageProvider;
    private final TaskRepository taskRepository;

    public TaskManager(JobScheduler jobScheduler, StorageProvider storageProvider, TaskRepository taskRepository) {
        this.jobScheduler = jobScheduler;
        this.storageProvider = storageProvider;
        this.taskRepository = taskRepository;
    }

    public void create(String name, String description) {
        Task task = taskRepository.save(Task.newTask(name, description));
        jobScheduler.<TaskHandler>enqueue(x -> x.executeTask(task.getId()));
        log.info("Task '{}' ({}) has been created.", task.getName(), task.getId());
    }

    public void schedule(LocalDateTime executionTime, String name, String description) {
        Instant createdAt = executionTime.atZone(ZoneId.systemDefault()).toInstant();
        Task task = taskRepository.save(Task.newTask(name, createdAt, description));
        jobScheduler.<TaskHandler>schedule(executionTime, x -> x.executeTask(task.getId()));
        log.info("Task '{}' ({}) has been scheduled at {}.", task.getName(), task.getId(), executionTime);
    }

    public void scheduleRecurrently(String cronExpression, String name, String description) {
        RecurringTask recurringTask = taskRepository.save(RecurringTask.newRecurringTask(name, description));
        jobScheduler.<RecurringTaskHandler>scheduleRecurrently(recurringTask.getName(), cronExpression, x -> x.executeTask(recurringTask.getId()));
        log.info("Task '{}' ({}) has been scheduled recurrently with cronExpression {}.", name, recurringTask.getId(), cronExpression);
    }

    public void deleteRecurringTask(String name) {
        RecurringTask recurringTask = taskRepository.getAllRecurringTasks()
                .stream()
                .filter(x -> x.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Recurring task with name " + name + " was not found"));
        jobScheduler.deleteRecurringJob(recurringTask.getName());
        List<Job> jobList = storageProvider.getJobList(StateName.SCHEDULED, Paging.AmountBasedList.ascOnUpdatedAt(1000));
        jobList.stream()
                .filter(j -> j.getRecurringJobId().map(recurringTask.getName()::equals).orElse(false))
                .map(Job::getId)
                .findFirst()
                .ifPresent(jobScheduler::delete);
        taskRepository.deleteRecurringTask(recurringTask.getId());
        log.info("Recurring task '{}' ({}) has been deleted.", name, recurringTask.getId());
    }

    public List<RecurringTask> getAllRecurringTasks() {
        return taskRepository.getAllRecurringTasks();
    }

    public void createTaskFromRecurringTask(RecurringTask recurringTask) {
        Task task = taskRepository.save(Task.newTask(recurringTask.getName(), recurringTask.getDescription()));
        jobScheduler.<TaskHandler>enqueue(x -> x.executeTask(task.getId()));
        log.info("Task '{}' ({}) has been created from recurring task.", task.getName(), task.getId());
    }
}
