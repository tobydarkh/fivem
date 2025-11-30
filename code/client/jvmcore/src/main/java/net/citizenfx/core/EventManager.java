package net.citizenfx.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages event handlers and event dispatching for the gamemode.
 */
public class EventManager {
    private static final Map<String, List<EventHandler>> handlers = new ConcurrentHashMap<>();

    /**
     * Register an event handler.
     *
     * @param eventName The name of the event to listen for
     * @param handler The handler function to call when the event is triggered
     */
    public static void on(String eventName, EventHandler handler) {
        handlers.computeIfAbsent(eventName, k -> new ArrayList<>()).add(handler);
    }

    /**
     * Register an event handler with a specific parameter type.
     *
     * @param eventName The name of the event to listen for
     * @param handler The handler function to call when the event is triggered
     * @param <T> The expected parameter type
     */
    public static <T> void on(String eventName, Consumer<T> handler) {
        on(eventName, args -> {
            if (args.length > 0) {
                @SuppressWarnings("unchecked")
                T arg = (T) args[0];
                handler.accept(arg);
            }
        });
    }

    /**
     * Unregister an event handler.
     *
     * @param eventName The name of the event
     * @param handler The handler to remove
     */
    public static void off(String eventName, EventHandler handler) {
        List<EventHandler> eventHandlers = handlers.get(eventName);
        if (eventHandlers != null) {
            eventHandlers.remove(handler);
        }
    }

    /**
     * Trigger an event locally.
     *
     * @param eventName The name of the event to trigger
     * @param args Arguments to pass to the event handlers
     */
    public static void trigger(String eventName, Object... args) {
        List<EventHandler> eventHandlers = handlers.get(eventName);
        if (eventHandlers != null) {
            for (EventHandler handler : eventHandlers) {
                try {
                    handler.handle(args);
                } catch (Exception e) {
                    ScriptInterface.printMessage("error",
                        "Exception in event handler for '" + eventName + "': " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Trigger an event on the server.
     *
     * @param eventName The name of the event to trigger
     * @param args Arguments to pass to the event
     */
    public static void triggerServer(String eventName, Object... args) {
        // TODO: Serialize args and send to server
        ScriptInterface.printMessage("script", "triggerServer: " + eventName);
    }

    /**
     * Trigger an event on a specific client (server-side only).
     *
     * @param target The target player/client
     * @param eventName The name of the event to trigger
     * @param args Arguments to pass to the event
     */
    public static void triggerClient(Object target, String eventName, Object... args) {
        // TODO: Serialize args and send to client
        ScriptInterface.printMessage("script", "triggerClient: " + eventName);
    }

    /**
     * Functional interface for event handlers.
     */
    @FunctionalInterface
    public interface EventHandler {
        void handle(Object... args);
    }
}
