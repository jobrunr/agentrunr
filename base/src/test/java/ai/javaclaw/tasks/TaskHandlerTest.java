package ai.javaclaw.tasks;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelRegistry;
import ai.javaclaw.tasks.Task.Status;
import ai.javaclaw.tasks.TaskHandler.TaskResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TaskHandlerTest {

    Agent agent;
    TaskRepository taskRepository;
    ChannelRegistry channelRegistry;
    Channel channel;
    TaskHandler taskHandler;

    @BeforeEach
    void setUp() {
        agent = mock(Agent.class);
        taskRepository = mock(TaskRepository.class);
        channelRegistry = mock(ChannelRegistry.class);
        channel = mock(Channel.class);
        taskHandler = new TaskHandler(agent, taskRepository, channelRegistry);
    }

    @Test
    void completedTaskNotifiesUser() {
        Task task = new Task("task-1", "check-email", Instant.now(), Status.todo, "Check inbox");
        when(taskRepository.getTaskById("task-1")).thenReturn(task);
        when(taskRepository.save(any(Task.class))).thenReturn(task);
        when(agent.prompt(eq("task-1"), anyString(), any())).thenReturn(new TaskResult(Status.completed, "Found 3 new emails"));
        when(channelRegistry.getLatestChannel()).thenReturn(channel);

        taskHandler.executeTask("task-1");

        verify(channel).sendMessage(contains("check-email"));
        verify(channel).sendMessage(contains("Found 3 new emails"));
    }

    @Test
    void awaitingHumanInputStillNotifies() {
        Task task = new Task("task-2", "review-doc", Instant.now(), Status.todo, "Review the document");
        when(taskRepository.getTaskById("task-2")).thenReturn(task);
        when(taskRepository.save(any(Task.class))).thenReturn(task);
        when(agent.prompt(eq("task-2"), anyString(), any())).thenReturn(new TaskResult(Status.awaiting_human_input, "Need your approval"));
        when(channelRegistry.getLatestChannel()).thenReturn(channel);

        taskHandler.executeTask("task-2");

        verify(channel).sendMessage(contains("awaiting_human_input"));
    }

    @Test
    void noChannelAvailableDoesNotThrow() {
        Task task = new Task("task-3", "some-task", Instant.now(), Status.todo, "Do something");
        when(taskRepository.getTaskById("task-3")).thenReturn(task);
        when(taskRepository.save(any(Task.class))).thenReturn(task);
        when(agent.prompt(eq("task-3"), anyString(), any())).thenReturn(new TaskResult(Status.completed, "Done"));
        when(channelRegistry.getLatestChannel()).thenReturn(null);

        taskHandler.executeTask("task-3");

        verify(channelRegistry).getLatestChannel();
    }

    @Test
    void notificationFailureDoesNotAffectTaskCompletion() {
        Task task = new Task("task-4", "failing-notify", Instant.now(), Status.todo, "Task with notify failure");
        when(taskRepository.getTaskById("task-4")).thenReturn(task);
        when(taskRepository.save(any(Task.class))).thenReturn(task);
        when(agent.prompt(eq("task-4"), anyString(), any())).thenReturn(new TaskResult(Status.completed, "All good"));
        when(channelRegistry.getLatestChannel()).thenReturn(channel);
        doThrow(new RuntimeException("Telegram down")).when(channel).sendMessage(anyString());

        taskHandler.executeTask("task-4");

        // Task should still be saved despite notification failure
        verify(taskRepository, times(2)).save(any(Task.class));
    }
}
