package net.citizenfx.core;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

/**
 * Debug support for JVM gamemodes.
 * Provides integration with Java debugging protocols (JDWP).
 */
public class DebugSupport {
    private static boolean debuggingEnabled = false;
    private static String debugAddress = "localhost:5005";

    /**
     * Check if debugging is enabled.
     */
    public static boolean isDebuggingEnabled() {
        return debuggingEnabled || isDebuggerAttached();
    }

    /**
     * Check if a debugger is currently attached via JDWP.
     */
    public static boolean isDebuggerAttached() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        List<String> arguments = runtimeMXBean.getInputArguments();

        for (String arg : arguments) {
            if (arg.contains("-agentlib:jdwp") || arg.contains("-Xrunjdwp")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get debug information.
     */
    public static String getDebugInfo() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();

        StringBuilder info = new StringBuilder();
        info.append("=== JVM Debug Information ===\n");
        info.append("JVM Name: ").append(runtimeMXBean.getVmName()).append("\n");
        info.append("JVM Version: ").append(runtimeMXBean.getVmVersion()).append("\n");
        info.append("JVM Vendor: ").append(runtimeMXBean.getVmVendor()).append("\n");
        info.append("Uptime: ").append(runtimeMXBean.getUptime()).append(" ms\n");
        info.append("Debugger Attached: ").append(isDebuggerAttached()).append("\n");

        if (isDebuggerAttached()) {
            info.append("\nJVM Arguments:\n");
            for (String arg : runtimeMXBean.getInputArguments()) {
                info.append("  ").append(arg).append("\n");
            }
        }

        return info.toString();
    }

    /**
     * Set debug address for JDWP.
     * Note: This only takes effect if set before JVM initialization.
     */
    public static void setDebugAddress(String address) {
        debugAddress = address;
    }

    /**
     * Get the debug address.
     */
    public static String getDebugAddress() {
        return debugAddress;
    }

    /**
     * Enable debugging mode.
     */
    public static void enableDebugging() {
        debuggingEnabled = true;
        ScriptInterface.printMessage("script", "Debug mode enabled");
    }

    /**
     * Disable debugging mode.
     */
    public static void disableDebugging() {
        debuggingEnabled = false;
        ScriptInterface.printMessage("script", "Debug mode disabled");
    }

    /**
     * Print stack trace for current thread.
     */
    public static void printStackTrace() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder("Stack trace:\n");

        for (int i = 2; i < stackTrace.length; i++) { // Skip getStackTrace and this method
            StackTraceElement element = stackTrace[i];
            sb.append("  at ").append(element.toString()).append("\n");
        }

        ScriptInterface.printMessage("script", sb.toString());
    }

    /**
     * Print all thread stack traces.
     */
    public static void printAllThreadStackTraces() {
        StringBuilder sb = new StringBuilder("=== All Thread Stack Traces ===\n");

        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            sb.append("\nThread: ").append(thread.getName())
              .append(" (").append(thread.getState()).append(")\n");

            StackTraceElement[] stackTrace = thread.getStackTrace();
            for (StackTraceElement element : stackTrace) {
                sb.append("  at ").append(element.toString()).append("\n");
            }
        }

        ScriptInterface.printMessage("script", sb.toString());
    }

    /**
     * Get memory usage information.
     */
    public static String getMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        StringBuilder info = new StringBuilder();
        info.append("=== Memory Information ===\n");
        info.append("Used Memory: ").append(formatBytes(usedMemory)).append("\n");
        info.append("Free Memory: ").append(formatBytes(freeMemory)).append("\n");
        info.append("Total Memory: ").append(formatBytes(totalMemory)).append("\n");
        info.append("Max Memory: ").append(formatBytes(maxMemory)).append("\n");

        return info.toString();
    }

    /**
     * Format bytes to human-readable format.
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Trigger garbage collection (for debugging only).
     */
    public static void forceGC() {
        ScriptInterface.printMessage("script", "Forcing garbage collection...");
        System.gc();
        System.runFinalization();
        ScriptInterface.printMessage("script", "Garbage collection completed");
    }

    /**
     * Get class loader information.
     */
    public static String getClassLoaderInfo() {
        ClassLoader cl = DebugSupport.class.getClassLoader();
        StringBuilder info = new StringBuilder();
        info.append("=== ClassLoader Information ===\n");

        while (cl != null) {
            info.append(cl.getClass().getName()).append("\n");
            cl = cl.getParent();
        }

        return info.toString();
    }
}
