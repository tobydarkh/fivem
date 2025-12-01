package com.example;

import net.citizenfx.core.BaseScript;
import net.citizenfx.core.DebugSupport;

/**
 * Example gamemode demonstrating JVM scripting for FiveM.
 * Shows usage of events, scheduling, coroutines, exports, and debug features.
 */
public class ExampleGamemode extends BaseScript {

    @Override
    public void onLoad() {
        print("===========================================");
        print("Example JVM Gamemode loaded successfully!");
        print("===========================================");

        // Register event handlers
        registerEventHandlers();

        // Set up scheduled tasks
        setupScheduledTasks();

        // Demonstrate coroutines
        demonstrateCoroutines();

        // Set up exports
        setupExports();

        // Print debug information
        if (DebugSupport.isDebuggingEnabled()) {
            print(DebugSupport.getDebugInfo());
            print(DebugSupport.getMemoryInfo());
        }

        print("Gamemode initialization complete.");
    }

    private void registerEventHandlers() {
        // Player connecting event
        on("playerConnecting", args -> {
            String playerName = args.length > 0 ? args[0].toString() : "Unknown";
            print("Player connecting: " + playerName);
        });

        // Player spawned event
        on("playerSpawned", args -> {
            print("Player spawned!");
        });

        // Chat message event
        on("chatMessage", args -> {
            if (args.length >= 2) {
                String author = args[0].toString();
                String message = args[1].toString();
                print(String.format("[Chat] %s: %s", author, message));

                // Handle commands
                if (message.startsWith("/debug")) {
                    handleDebugCommand();
                }
            }
        });

        // Custom event example
        on("exampleEvent", args -> {
            print("Custom event triggered!");
            if (args.length > 0) {
                print("Event data: " + args[0].toString());
            }
        });
    }

    private void setupScheduledTasks() {
        // Print message every 5 seconds
        scheduleRepeating(5000, () -> {
            print("Heartbeat: Gamemode is running...");
        });

        // One-time delayed task
        scheduleDelayed(10000, () -> {
            print("This message appears 10 seconds after load");
        });
    }

    private void demonstrateCoroutines() {
        // Example coroutine with delays
        startCoroutine(yield -> {
            print("Coroutine: Starting...");

            yield.yield(delay(1000));
            print("Coroutine: After 1 second");

            yield.yield(delay(2000));
            print("Coroutine: After 3 seconds total");

            yield.yield(waitForNextFrame());
            print("Coroutine: Next frame");

            // Wait for condition
            yield.yield(waitUntil(() -> System.currentTimeMillis() % 10 == 0));
            print("Coroutine: Condition met!");
        });

        // Another coroutine example
        startCoroutine(yield -> {
            for (int i = 1; i <= 3; i++) {
                print("Count: " + i);
                yield.yield(delay(1500));
            }
            print("Counting complete!");
        });
    }

    private void setupExports() {
        // Export individual functions
        export("getPlayerCount", args -> {
            // In real implementation, get actual player count
            return 0;
        });

        export("sendMessage", args -> {
            if (args.length > 0) {
                String message = args[0].toString();
                print("Message from export: " + message);
                return true;
            }
            return false;
        });

        // Export a method with specific functionality
        export("getGamemodeInfo", args -> {
            return "Example JVM Gamemode v1.0.0";
        });

        print("Exports registered: getPlayerCount, sendMessage, getGamemodeInfo");
    }

    // Public methods can be auto-exported
    public String getVersion() {
        return "1.0.0";
    }

    public void customMethod(String param) {
        print("Custom method called with: " + param);
    }

    private void handleDebugCommand() {
        print(DebugSupport.getDebugInfo());
        print(DebugSupport.getMemoryInfo());
        print(DebugSupport.getClassLoaderInfo());
    }

    @Override
    public void onUnload() {
        print("Example JVM Gamemode unloading...");
        super.onUnload();
    }
}
