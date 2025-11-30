package net.citizenfx.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for FiveM gamemodes written in Java.
 * Extend this class to create your gamemode.
 */
public abstract class BaseScript {
    private final List<EventManager.EventHandler> registeredHandlers = new ArrayList<>();

    /**
     * Called when the script is loaded.
     * Override this method to initialize your gamemode.
     */
    public void onLoad() {
        // Override in subclass
    }

    /**
     * Called when the script is unloaded.
     * Override this method to clean up resources.
     */
    public void onUnload() {
        // Unregister all event handlers
        for (EventManager.EventHandler handler : registeredHandlers) {
            // TODO: Unregister handlers
        }
        registeredHandlers.clear();
    }

    /**
     * Register an event handler.
     *
     * @param eventName The name of the event to listen for
     * @param handler The handler function
     */
    protected void on(String eventName, EventManager.EventHandler handler) {
        EventManager.on(eventName, handler);
        registeredHandlers.add(handler);
    }

    /**
     * Trigger an event locally.
     *
     * @param eventName The name of the event to trigger
     * @param args Arguments to pass to the event handlers
     */
    protected void trigger(String eventName, Object... args) {
        EventManager.trigger(eventName, args);
    }

    /**
     * Trigger an event on the server.
     *
     * @param eventName The name of the event to trigger
     * @param args Arguments to pass to the event
     */
    protected void triggerServer(String eventName, Object... args) {
        EventManager.triggerServer(eventName, args);
    }

    /**
     * Trigger an event on a specific client (server-side only).
     *
     * @param target The target player/client
     * @param eventName The name of the event to trigger
     * @param args Arguments to pass to the event
     */
    protected void triggerClient(Object target, String eventName, Object... args) {
        EventManager.triggerClient(target, eventName, args);
    }

    /**
     * Print a message to the console.
     *
     * @param message The message to print
     */
    protected void print(String message) {
        ScriptInterface.printMessage("script", message);
    }

    /**
     * Print an error message to the console.
     *
     * @param message The error message to print
     */
    protected void printError(String message) {
        ScriptInterface.printMessage("error", message);
    }

    /**
     * Print a warning message to the console.
     *
     * @param message The warning message to print
     */
    protected void printWarning(String message) {
        ScriptInterface.printMessage("warning", message);
    }

    /**
     * Invoke a native function.
     *
     * @param hash The native function hash
     * @param args Arguments to pass to the native
     * @return The result of the native invocation
     */
    protected Object invokeNative(long hash, Object... args) {
        return Native.invoke(hash, args);
    }

    /**
     * Invoke a native function with no return value.
     *
     * @param hash The native function hash
     * @param args Arguments to pass to the native
     */
    protected void invokeNativeVoid(long hash, Object... args) {
        Native.invokeVoid(hash, args);
    }

    // Scheduler methods

    /**
     * Schedule a task to run after a delay.
     *
     * @param delayMs Delay in milliseconds
     * @param action Action to run
     */
    protected void scheduleDelayed(long delayMs, Runnable action) {
        Scheduler.scheduleDelayed(delayMs, action);
    }

    /**
     * Schedule a repeating task.
     *
     * @param intervalMs Interval in milliseconds
     * @param action Action to run
     * @return Scheduled task (can be canceled)
     */
    protected Scheduler.ScheduledTask scheduleRepeating(long intervalMs, Runnable action) {
        return Scheduler.scheduleRepeating(intervalMs, action);
    }

    /**
     * Schedule a task to run on the next tick.
     *
     * @param action Action to run
     */
    protected void scheduleNextTick(Runnable action) {
        Scheduler.scheduleNextTick(action);
    }

    /**
     * Start a coroutine.
     *
     * @param function Coroutine function
     * @return Coroutine instance
     */
    protected Scheduler.Coroutine startCoroutine(Scheduler.CoroutineFunction function) {
        return Scheduler.startCoroutine(function);
    }

    /**
     * Create a delay yield instruction (for use in coroutines).
     *
     * @param milliseconds Delay in milliseconds
     * @return Delay instruction
     */
    protected Scheduler.Delay delay(long milliseconds) {
        return Scheduler.delay(milliseconds);
    }

    /**
     * Wait until the next frame (for use in coroutines).
     *
     * @return Wait instruction
     */
    protected Scheduler.WaitForNextFrame waitForNextFrame() {
        return Scheduler.waitForNextFrame();
    }

    /**
     * Wait until a condition is met (for use in coroutines).
     *
     * @param condition Condition to wait for
     * @return Wait instruction
     */
    protected Scheduler.WaitUntil waitUntil(Scheduler.BooleanSupplier condition) {
        return Scheduler.waitUntil(condition);
    }
}
