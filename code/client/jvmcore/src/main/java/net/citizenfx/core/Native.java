package net.citizenfx.core;

/**
 * Utility class for invoking game natives from Java.
 * This provides a bridge to the native function invocation system.
 */
public class Native {
    // Thread-local context to avoid allocations
    private static final ThreadLocal<NativeContext> context = ThreadLocal.withInitial(NativeContext::new);

    // Native methods (implemented in C++)
    private static native long getNativePointer(long hash);
    private static native void invokeNativeInternal(long nativePtr, long hash, long[] args, int argCount,
                                                     long[] returnData, int[] returnCount, byte[] stringHeap);

    /**
     * Invoke a native function by hash.
     *
     * @param hash The native function hash
     * @param args Arguments to pass to the native
     * @return The result of the native invocation
     */
    public static Object invoke(long hash, Object... args) {
        NativeContext ctx = context.get();
        ctx.reset();

        // Marshal arguments
        for (Object arg : args) {
            if (arg == null) {
                ctx.pushArg(0L);
            } else if (arg instanceof Integer) {
                ctx.pushArg((Integer) arg);
            } else if (arg instanceof Long) {
                ctx.pushArg((Long) arg);
            } else if (arg instanceof Float) {
                ctx.pushArg((Float) arg);
            } else if (arg instanceof Double) {
                ctx.pushArg((Double) arg);
            } else if (arg instanceof Boolean) {
                ctx.pushArg((Boolean) arg);
            } else if (arg instanceof String) {
                ctx.pushArg((String) arg);
            } else if (arg instanceof Vector3) {
                ctx.pushArg((Vector3) arg);
            } else {
                ctx.pushArg(arg);
            }
        }

        // Get native pointer and invoke
        long nativePtr = getNativePointer(hash);
        if (nativePtr == 0) {
            ScriptInterface.printMessage("error", String.format("Native 0x%016X not found", hash));
            return null;
        }

        long[] returnData = new long[32];
        int[] returnCount = new int[1];

        invokeNativeInternal(nativePtr, hash, ctx.getArguments(), ctx.getArgumentCount(),
                           returnData, returnCount, ctx.getStringHeap());

        ctx.setReturnData(returnData, returnCount[0]);

        // Return as object (caller can cast)
        if (returnCount[0] > 0) {
            return returnData[0];
        }
        return null;
    }

    /**
     * Invoke a native function by hash with a specific return type.
     *
     * @param returnType The expected return type
     * @param hash The native function hash
     * @param args Arguments to pass to the native
     * @return The result of the native invocation
     */
    @SuppressWarnings("unchecked")
    public static <T> T invoke(Class<T> returnType, long hash, Object... args) {
        return (T) invoke(hash, args);
    }

    /**
     * Invoke a native function and expect no return value.
     *
     * @param hash The native function hash
     * @param args Arguments to pass to the native
     */
    public static void invokeVoid(long hash, Object... args) {
        invoke(hash, args);
    }

    /**
     * Invoke a native function and expect an integer return value.
     *
     * @param hash The native function hash
     * @param args Arguments to pass to the native
     * @return The integer result
     */
    public static int invokeInt(long hash, Object... args) {
        Object result = invoke(hash, args);
        return result instanceof Integer ? (Integer) result : 0;
    }

    /**
     * Invoke a native function and expect a long return value.
     *
     * @param hash The native function hash
     * @param args Arguments to pass to the native
     * @return The long result
     */
    public static long invokeLong(long hash, Object... args) {
        Object result = invoke(hash, args);
        return result instanceof Long ? (Long) result : 0L;
    }

    /**
     * Invoke a native function and expect a float return value.
     *
     * @param hash The native function hash
     * @param args Arguments to pass to the native
     * @return The float result
     */
    public static float invokeFloat(long hash, Object... args) {
        Object result = invoke(hash, args);
        return result instanceof Float ? (Float) result : 0.0f;
    }

    /**
     * Invoke a native function and expect a boolean return value.
     *
     * @param hash The native function hash
     * @param args Arguments to pass to the native
     * @return The boolean result
     */
    public static boolean invokeBoolean(long hash, Object... args) {
        Object result = invoke(hash, args);
        return result instanceof Boolean ? (Boolean) result : false;
    }

    /**
     * Invoke a native function and expect a string return value.
     *
     * @param hash The native function hash
     * @param args Arguments to pass to the native
     * @return The string result
     */
    public static String invokeString(long hash, Object... args) {
        Object result = invoke(hash, args);
        return result instanceof String ? (String) result : null;
    }
}
