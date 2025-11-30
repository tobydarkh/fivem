package net.citizenfx.core;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Scheduler for async tasks and coroutines in FiveM gamemodes.
 * Provides cooperative multitasking with tick-based scheduling.
 */
public class Scheduler {
    // Priority queue for scheduled tasks
    private static final PriorityQueue<ScheduledTask> tasks = new PriorityQueue<>(
        Comparator.comparingLong(t -> t.scheduledTime)
    );

    // Tasks to add (to avoid concurrent modification)
    private static final Queue<ScheduledTask> pendingTasks = new ConcurrentLinkedQueue<>();

    // Current game time
    private static long currentTime = 0;

    /**
     * Process all scheduled tasks for the current tick.
     */
    public static void tick(long gameTime) {
        currentTime = gameTime;

        // Add pending tasks
        while (!pendingTasks.isEmpty()) {
            tasks.add(pendingTasks.poll());
        }

        // Process tasks that are ready
        while (!tasks.isEmpty() && tasks.peek().scheduledTime <= currentTime) {
            ScheduledTask task = tasks.poll();

            try {
                task.run();
            } catch (Exception e) {
                ScriptInterface.printMessage("error",
                    "Exception in scheduled task: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Get the next scheduled time (for tickless scheduling).
     */
    public static long getNextScheduledTime() {
        if (tasks.isEmpty()) {
            return Long.MAX_VALUE;
        }
        return tasks.peek().scheduledTime;
    }

    /**
     * Schedule a task to run at a specific time.
     */
    public static void schedule(long time, Runnable action) {
        pendingTasks.add(new ScheduledTask(time, action, false));
    }

    /**
     * Schedule a task to run after a delay.
     */
    public static void scheduleDelayed(long delayMs, Runnable action) {
        pendingTasks.add(new ScheduledTask(currentTime + delayMs, action, false));
    }

    /**
     * Schedule a repeating task.
     */
    public static ScheduledTask scheduleRepeating(long intervalMs, Runnable action) {
        ScheduledTask task = new ScheduledTask(currentTime + intervalMs, action, true);
        task.intervalMs = intervalMs;
        pendingTasks.add(task);
        return task;
    }

    /**
     * Schedule a task to run on the next tick.
     */
    public static void scheduleNextTick(Runnable action) {
        pendingTasks.add(new ScheduledTask(currentTime + 1, action, false));
    }

    /**
     * Cancel a scheduled task.
     */
    public static void cancel(ScheduledTask task) {
        tasks.remove(task);
    }

    /**
     * Create and run a coroutine.
     */
    public static Coroutine startCoroutine(CoroutineFunction function) {
        Coroutine coroutine = new Coroutine(function);
        scheduleNextTick(coroutine::resume);
        return coroutine;
    }

    /**
     * Delay execution for a specified amount of time (coroutine yield).
     */
    public static Delay delay(long milliseconds) {
        return new Delay(milliseconds);
    }

    /**
     * Wait until the next frame (coroutine yield).
     */
    public static WaitForNextFrame waitForNextFrame() {
        return new WaitForNextFrame();
    }

    /**
     * Wait until a condition is met (coroutine yield).
     */
    public static WaitUntil waitUntil(BooleanSupplier condition) {
        return new WaitUntil(condition);
    }

    // Scheduled task wrapper
    public static class ScheduledTask {
        long scheduledTime;
        Runnable action;
        boolean repeating;
        long intervalMs;

        ScheduledTask(long scheduledTime, Runnable action, boolean repeating) {
            this.scheduledTime = scheduledTime;
            this.action = action;
            this.repeating = repeating;
        }

        void run() {
            action.run();
            if (repeating) {
                scheduledTime = currentTime + intervalMs;
                tasks.add(this);
            }
        }
    }

    // Coroutine support
    @FunctionalInterface
    public interface CoroutineFunction {
        void run(CoroutineYield yield) throws Exception;
    }

    @FunctionalInterface
    public interface BooleanSupplier {
        boolean get();
    }

    public static class Coroutine {
        private final CoroutineFunction function;
        private final CoroutineYield yield;
        private boolean completed;

        Coroutine(CoroutineFunction function) {
            this.function = function;
            this.yield = new CoroutineYield(this);
            this.completed = false;
        }

        void resume() {
            if (completed) return;

            try {
                function.run(yield);
                completed = true;
            } catch (YieldException e) {
                // Coroutine yielded, schedule continuation
                e.yieldInstruction.schedule(this);
            } catch (Exception e) {
                ScriptInterface.printMessage("error",
                    "Exception in coroutine: " + e.getMessage());
                e.printStackTrace();
                completed = true;
            }
        }

        public boolean isCompleted() {
            return completed;
        }
    }

    public static class CoroutineYield {
        private final Coroutine coroutine;

        CoroutineYield(Coroutine coroutine) {
            this.coroutine = coroutine;
        }

        public void yield(YieldInstruction instruction) {
            throw new YieldException(instruction);
        }
    }

    private static class YieldException extends RuntimeException {
        final YieldInstruction yieldInstruction;

        YieldException(YieldInstruction instruction) {
            super(null, null, false, false); // No stack trace for control flow
            this.yieldInstruction = instruction;
        }
    }

    public interface YieldInstruction {
        void schedule(Coroutine coroutine);
    }

    public static class Delay implements YieldInstruction {
        private final long milliseconds;

        Delay(long milliseconds) {
            this.milliseconds = milliseconds;
        }

        @Override
        public void schedule(Coroutine coroutine) {
            scheduleDelayed(milliseconds, coroutine::resume);
        }
    }

    public static class WaitForNextFrame implements YieldInstruction {
        @Override
        public void schedule(Coroutine coroutine) {
            scheduleNextTick(coroutine::resume);
        }
    }

    public static class WaitUntil implements YieldInstruction {
        private final BooleanSupplier condition;

        WaitUntil(BooleanSupplier condition) {
            this.condition = condition;
        }

        @Override
        public void schedule(Coroutine coroutine) {
            if (condition.get()) {
                scheduleNextTick(coroutine::resume);
            } else {
                scheduleDelayed(10, () -> schedule(coroutine));
            }
        }
    }
}
