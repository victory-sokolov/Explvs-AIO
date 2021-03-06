package tasks.task_executor;

import org.osbot.rs07.script.RandomEvent;
import tasks.LoopTask;
import tasks.Task;
import tasks.TaskType;
import tasks.break_task.CustomBreakManager;
import util.Executable;

import java.util.*;

public final class TaskExecutor extends Executable {

    private final List<Task> allTasks;
    private final Queue<Task> taskQueue = new LinkedList<>();
    private final List<TaskChangeListener> taskChangeListeners = new ArrayList<>();
    private Task currentTask;

    public TaskExecutor(final List<Task> tasks) {
        allTasks = tasks;
        this.taskQueue.addAll(tasks);
    }

    public Task getCurrentTask() {
        return currentTask;
    }

    public void setTaskQueue(final List<Task> taskQueue) {
        this.taskQueue.clear();
        this.taskQueue.addAll(taskQueue);
        currentTask = null;
    }

    public final void addTaskChangeListener(final TaskChangeListener taskChangeListener) {
        this.taskChangeListeners.add(taskChangeListener);
    }

    public final void addTaskChangeListeners(final Collection<TaskChangeListener> taskChangeListeners) {
        this.taskChangeListeners.addAll(taskChangeListeners);
    }

    public boolean isComplete() {
        return taskQueue.isEmpty() && (currentTask == null || currentTask.hasFailed() || currentTask.isComplete());
    }

    @Override
    public void onStart() throws InterruptedException {
        if (taskQueue.stream().anyMatch(task -> task.getTaskType() == TaskType.BREAK)) {
            // If we haven't already overridden the break manager
            if (!(getBot().getRandomExecutor().forEvent(RandomEvent.BREAK_MANAGER) instanceof CustomBreakManager)) {
                CustomBreakManager customBreakManager = new CustomBreakManager();
                customBreakManager.exchangeContext(getBot());
                getBot().getRandomExecutor().overrideOSBotRandom(customBreakManager);
            }
        }
    }

    @Override
    public final void run() throws InterruptedException {
        if (currentTask == null || (currentTask.isComplete() && currentTask.canExit()) || currentTask.hasFailed()) {
            loadNextTask(taskQueue);
        } else {
            runTask(currentTask);
        }
    }

    private void loadNextTask(Queue<Task> taskQueue) throws InterruptedException {
        Task prevTask = currentTask;
        currentTask = taskQueue.poll();

        if (currentTask == null) {
            return;
        }

        currentTask.exchangeContext(getBot());

        if (currentTask instanceof LoopTask) {
            ((LoopTask) currentTask).setup(allTasks, taskChangeListeners);
        }

        currentTask.onStart();

        for (final TaskChangeListener taskChangeListener : taskChangeListeners) {
            taskChangeListener.taskChanged(prevTask, currentTask);
        }
    }

    private void runTask(final Task task) throws InterruptedException {
        try {
            task.run();
        } catch (NullPointerException nullPointer) {
            log("Found null pointer exception. Task failed, exiting.");

            StackTraceElement[] stack = nullPointer.getStackTrace();
            for (StackTraceElement element : stack) {
                log(element.toString());
            }

            currentTask = null;
            taskQueue.clear();
        }
    }
}
