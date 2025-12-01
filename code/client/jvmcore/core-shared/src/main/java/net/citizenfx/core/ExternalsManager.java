package net.citizenfx.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages externals - calling exports from other resources.
 * Provides a way to call APIs exposed by other gamemodes/resources.
 */
public class ExternalsManager {
    // Cache of resource handles
    private static final Map<String, ResourceHandle> resourceCache = new ConcurrentHashMap<>();

    // Native method to call external export
    private static native byte[] callExternalNative(String resourceName, String exportName, byte[] argsSerialized);

    /**
     * Get a handle to another resource.
     *
     * @param resourceName Name of the resource
     * @return Resource handle
     */
    public static ResourceHandle getResource(String resourceName) {
        return resourceCache.computeIfAbsent(resourceName, ResourceHandle::new);
    }

    /**
     * Call an export from another resource.
     *
     * @param resourceName Name of the resource
     * @param exportName Name of the export
     * @param args Arguments to pass
     * @return Result from the export
     */
    public static Object call(String resourceName, String exportName, Object... args) {
        try {
            // Serialize arguments
            byte[] argsSerialized = new byte[0];
            if (args != null && args.length > 0) {
                argsSerialized = MsgPackSerializer.serialize(java.util.Arrays.asList(args));
            }

            // Call native method
            byte[] resultSerialized = callExternalNative(resourceName, exportName, argsSerialized);

            // Deserialize result
            if (resultSerialized != null && resultSerialized.length > 0) {
                return MsgPackSerializer.deserialize(resultSerialized);
            }
        } catch (Exception e) {
            ScriptInterface.printMessage("error",
                String.format("Error calling external %s:%s - %s", resourceName, exportName, e.getMessage()));
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Call an export from another resource with a specific return type.
     *
     * @param returnType Expected return type
     * @param resourceName Name of the resource
     * @param exportName Name of the export
     * @param args Arguments to pass
     * @return Result from the export
     */
    @SuppressWarnings("unchecked")
    public static <T> T call(Class<T> returnType, String resourceName, String exportName, Object... args) {
        Object result = call(resourceName, exportName, args);
        if (result != null && returnType.isInstance(result)) {
            return (T) result;
        }
        return null;
    }

    /**
     * Handle to a resource for calling its exports.
     */
    public static class ResourceHandle {
        private final String resourceName;

        ResourceHandle(String resourceName) {
            this.resourceName = resourceName;
        }

        /**
         * Call an export from this resource.
         *
         * @param exportName Name of the export
         * @param args Arguments to pass
         * @return Result from the export
         */
        public Object call(String exportName, Object... args) {
            return ExternalsManager.call(resourceName, exportName, args);
        }

        /**
         * Call an export with a specific return type.
         *
         * @param returnType Expected return type
         * @param exportName Name of the export
         * @param args Arguments to pass
         * @return Result from the export
         */
        public <T> T call(Class<T> returnType, String exportName, Object... args) {
            return ExternalsManager.call(returnType, resourceName, exportName, args);
        }

        /**
         * Get the resource name.
         */
        public String getResourceName() {
            return resourceName;
        }
    }
}
