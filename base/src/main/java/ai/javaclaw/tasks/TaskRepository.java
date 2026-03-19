package ai.javaclaw.tasks;

import java.time.LocalDate;
import java.util.List;

public interface TaskRepository {

    Task save(Task task);

    Task getTaskById(String id);

    List<Task> getTasks(LocalDate localDate, Task.Status status);

    RecurringTask save(RecurringTask recurringTask);

    RecurringTask getRecurringTaskById(String id);

    List<RecurringTask> getAllRecurringTasks();

    void deleteRecurringTask(String id);

}
