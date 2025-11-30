package com.example;

import net.citizenfx.core.BaseScript;
import net.citizenfx.core.EventManager;

/**
 * Example gamemode demonstrating JVM scripting for FiveM.
 */
public class ExampleGamemode extends BaseScript {

    @Override
    public void onLoad() {
        print("===========================================");
        print("Example JVM Gamemode loaded successfully!");
        print("===========================================");

        // Register event handlers
        registerEventHandlers();

        // Example: Print a message every 5 seconds
        // TODO: Implement timer/scheduler system
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

    @Override
    public void onUnload() {
        print("Example JVM Gamemode unloading...");
        super.onUnload();
    }
}
