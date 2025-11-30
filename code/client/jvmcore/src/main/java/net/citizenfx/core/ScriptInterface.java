package net.citizenfx.core;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main bridge interface between native C++ code and Java/JVM runtime.
 * This class provides core functionality for FiveM gamemode scripting in Java.
 */
public class ScriptInterface {
    private static String resourceName;
    private static long runtimePtr;
    private static int instanceId;
    private static long sharedDataPtr;

    private static final AtomicLong nextScheduledTime = new AtomicLong(Long.MAX_VALUE);
    private static final ConcurrentHashMap<Integer, Object> references = new ConcurrentHashMap<>();
    private static int nextRefId = 1;

    // Native methods implemented in C++
    private static native void print(String channel, String text);
    private static native long getNative(long hash);
    private static native boolean invokeNative(long nativePtr, Object context, long hash);
    private static native void cfree(long ptr);

    private static native boolean profilerIsRecording();
    private static native void profilerEnterScope(String name);
    private static native void profilerExitScope();

    private static native byte[] canonicalizeRef(long runtime, int refId);
    private static native byte[] invokeFunctionReference(long runtime, String refId, byte[] args);
    private static native boolean readClass(long runtime, String name, byte[][] outBytes);

    /**
     * Initialize the script interface for a resource.
     * Called from C++ when creating a new runtime.
     */
    public static void initialize(String resName, long runtime, int instId, long sharedData) {
        resourceName = resName;
        runtimePtr = runtime;
        instanceId = instId;
        sharedDataPtr = sharedData;

        print("script", String.format("Initialized JVM gamemode runtime for resource: %s (instance: %d)",
            resourceName, instanceId));
    }

    /**
     * Main tick function called from C++ every frame (when scheduled).
     */
    public static void tick(long gameTime, boolean profiling) {
        try {
            if (profiling && profilerIsRecording()) {
                profilerEnterScope("JVM.Tick");
            }

            // Process scheduled tasks and coroutines
            Scheduler.tick(gameTime);

            // Request next tick based on scheduler's next scheduled time
            long nextScheduledTime = Scheduler.getNextScheduledTime();
            requestTick(nextScheduledTime);

            if (profiling && profilerIsRecording()) {
                profilerExitScope();
            }
        } catch (Exception e) {
            print("error", "Exception in tick: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Trigger an event in this resource.
     */
    public static void triggerEvent(String eventName, byte[] argsSerialized, String sourceId,
                                    long gameTime, boolean profiling) {
        try {
            if (profiling && profilerIsRecording()) {
                profilerEnterScope("JVM.TriggerEvent." + eventName);
            }

            // Deserialize arguments
            Object[] args = new Object[0];
            if (argsSerialized != null && argsSerialized.length > 0) {
                try {
                    Object deserialized = MsgPackSerializer.deserialize(argsSerialized);
                    if (deserialized instanceof java.util.List) {
                        java.util.List<?> list = (java.util.List<?>) deserialized;
                        args = list.toArray();
                    } else if (deserialized != null) {
                        args = new Object[] { deserialized };
                    }
                } catch (Exception e) {
                    print("error", "Failed to deserialize event args: " + e.getMessage());
                }
            }

            // Dispatch to event manager
            EventManager.trigger(eventName, args);

            if (profiling && profilerIsRecording()) {
                profilerExitScope();
            }
        } catch (Exception e) {
            print("error", "Exception in triggerEvent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load a JAR/class file.
     */
    public static void loadClass(String file) {
        try {
            print("script", "Loading class/JAR: " + file);
            // TODO: Load JAR using URLClassLoader and instantiate main class
        } catch (Exception e) {
            print("error", "Exception in loadClass: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Call a reference (cross-runtime function call).
     */
    public static byte[] callRef(int refIdx, byte[] argsSerialized, long gameTime, boolean profiling) {
        try {
            if (profiling && profilerIsRecording()) {
                profilerEnterScope("JVM.CallRef");
            }

            Object ref = references.get(refIdx);
            if (ref != null) {
                // TODO: Deserialize args and invoke the reference
            }

            if (profiling && profilerIsRecording()) {
                profilerExitScope();
            }

            return new byte[0];
        } catch (Exception e) {
            print("error", "Exception in callRef: " + e.getMessage());
            e.printStackTrace();
            return new byte[0];
        }
    }

    /**
     * Duplicate a reference.
     */
    public static int duplicateRef(int refIdx) {
        Object ref = references.get(refIdx);
        if (ref != null) {
            int newRefIdx = nextRefId++;
            references.put(newRefIdx, ref);
            return newRefIdx;
        }
        return -1;
    }

    /**
     * Remove a reference.
     */
    public static void removeRef(int refIdx) {
        references.remove(refIdx);
    }

    /**
     * Get current memory usage.
     */
    public static long getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * Start profiling.
     */
    public static void startProfiling() {
        // TODO: Set up profiling
    }

    /**
     * Stop profiling.
     */
    public static void stopProfiling() {
        // TODO: Tear down profiling
    }

    /**
     * Request a tick at the specified time.
     * This implements the tickless scheduling system.
     */
    public static void requestTick(long time) {
        nextScheduledTime.set(time);
        // Update the shared data in C++
        if (sharedDataPtr != 0) {
            // TODO: Use sun.misc.Unsafe or JNI to write to shared memory
            // For now, we rely on C++ checking our nextScheduledTime
        }
    }

    /**
     * Print to console.
     */
    public static void printMessage(String channel, String message) {
        print(channel, message);
    }

    /**
     * Get the resource name.
     */
    public static String getResourceName() {
        return resourceName;
    }

    /**
     * Get the instance ID.
     */
    public static int getInstanceId() {
        return instanceId;
    }
}
