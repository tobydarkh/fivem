package net.citizenfx.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages exports - functions that can be called from other resources.
 * Provides a way to expose APIs to other gamemodes/resources.
 */
public class ExportsManager {
    // Map of export name -> ExportedFunction
    private static final Map<String, ExportedFunction> exports = new ConcurrentHashMap<>();

    /**
     * Register an export.
     *
     * @param name Export name
     * @param function Function to export
     */
    public static void add(String name, ExportedFunction function) {
        exports.put(name, function);
        ScriptInterface.printMessage("script", "Registered export: " + name);
    }

    /**
     * Remove an export.
     *
     * @param name Export name
     */
    public static void remove(String name) {
        exports.remove(name);
    }

    /**
     * Check if an export exists.
     *
     * @param name Export name
     * @return True if export exists
     */
    public static boolean has(String name) {
        return exports.containsKey(name);
    }

    /**
     * Call an export.
     *
     * @param name Export name
     * @param args Arguments to pass
     * @return Result from the export
     */
    public static Object call(String name, Object... args) {
        ExportedFunction function = exports.get(name);
        if (function == null) {
            throw new IllegalArgumentException("Export not found: " + name);
        }

        try {
            return function.call(args);
        } catch (Exception e) {
            ScriptInterface.printMessage("error", "Error calling export '" + name + "': " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Call an export from C++ (internal use).
     */
    public static byte[] callFromNative(String name, byte[] argsSerialized) {
        try {
            // Deserialize arguments
            Object[] args = new Object[0];
            if (argsSerialized != null && argsSerialized.length > 0) {
                Object deserialized = MsgPackSerializer.deserialize(argsSerialized);
                if (deserialized instanceof java.util.List) {
                    java.util.List<?> list = (java.util.List<?>) deserialized;
                    args = list.toArray();
                } else if (deserialized != null) {
                    args = new Object[] { deserialized };
                }
            }

            // Call export
            Object result = call(name, args);

            // Serialize result
            if (result != null) {
                return MsgPackSerializer.serialize(result);
            }
        } catch (Exception e) {
            ScriptInterface.printMessage("error", "Error in callFromNative: " + e.getMessage());
            e.printStackTrace();
        }

        return new byte[0];
    }

    /**
     * Functional interface for exported functions.
     */
    @FunctionalInterface
    public interface ExportedFunction {
        Object call(Object... args) throws Exception;
    }

    /**
     * Helper to export a method from an object.
     */
    public static class MethodExporter {
        private final Object instance;
        private final Method method;

        public MethodExporter(Object instance, Method method) {
            this.instance = instance;
            this.method = method;
            method.setAccessible(true);
        }

        public Object call(Object... args) throws Exception {
            try {
                return method.invoke(instance, args);
            } catch (InvocationTargetException e) {
                throw (Exception) e.getCause();
            }
        }
    }

    /**
     * Auto-export all public methods from an object.
     *
     * @param instance Object to export methods from
     * @param prefix Optional prefix for export names
     */
    public static void exportObject(Object instance, String prefix) {
        Class<?> clazz = instance.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                String exportName = (prefix != null ? prefix + ":" : "") + method.getName();
                MethodExporter exporter = new MethodExporter(instance, method);
                add(exportName, exporter::call);
            }
        }
    }

    /**
     * Export all public methods from an object without prefix.
     *
     * @param instance Object to export methods from
     */
    public static void exportObject(Object instance) {
        exportObject(instance, null);
    }
}
