package net.citizenfx.core.client;

import net.citizenfx.core.Vector3;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Represents the native script context for marshaling arguments and results (CLIENT-SIDE).
 * This mirrors the C++ rage::scrNativeCallContext structure.
 */
public class NativeContext {
    // Native context data (aligned 8-byte values)
    private final long[] arguments;
    private int argumentCount;

    // Return data
    private final long[] returnData;
    private int returnCount;

    // String heap for temporary string storage
    private static final int STRING_HEAP_SIZE = 64 * 1024;
    private static final byte[] stringHeap = new byte[STRING_HEAP_SIZE];
    private static int stringHeapPos = 0;

    public NativeContext() {
        // Maximum 32 arguments (matching native limit)
        this.arguments = new long[32];
        this.returnData = new long[32];
        this.argumentCount = 0;
        this.returnCount = 0;
    }

    /**
     * Reset the context for a new call.
     */
    public void reset() {
        argumentCount = 0;
        returnCount = 0;
        stringHeapPos = 0;
    }

    /**
     * Push an integer argument.
     */
    public void pushArg(int value) {
        arguments[argumentCount++] = value;
    }

    /**
     * Push a long argument.
     */
    public void pushArg(long value) {
        arguments[argumentCount++] = value;
    }

    /**
     * Push a float argument.
     */
    public void pushArg(float value) {
        arguments[argumentCount++] = Float.floatToRawIntBits(value);
    }

    /**
     * Push a double argument.
     */
    public void pushArg(double value) {
        arguments[argumentCount++] = Double.doubleToRawLongBits(value);
    }

    /**
     * Push a boolean argument.
     */
    public void pushArg(boolean value) {
        arguments[argumentCount++] = value ? 1 : 0;
    }

    /**
     * Push a string argument (converts to C string).
     */
    public void pushArg(String value) {
        if (value == null) {
            arguments[argumentCount++] = 0;
            return;
        }

        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int heapPtr = stringHeapPos;

        if (heapPtr + bytes.length + 1 < STRING_HEAP_SIZE) {
            System.arraycopy(bytes, 0, stringHeap, heapPtr, bytes.length);
            stringHeap[heapPtr + bytes.length] = 0; // null terminator
            stringHeapPos += bytes.length + 1;

            // Store pointer (we'll need to convert this to native pointer in C++)
            arguments[argumentCount++] = heapPtr;
        } else {
            // Heap full, use null
            arguments[argumentCount++] = 0;
        }
    }

    /**
     * Push a Vector3 argument.
     */
    public void pushArg(Vector3 value) {
        if (value == null) {
            arguments[argumentCount++] = 0;
            arguments[argumentCount++] = 0;
            arguments[argumentCount++] = 0;
        } else {
            arguments[argumentCount++] = Float.floatToRawIntBits(value.x);
            arguments[argumentCount++] = Float.floatToRawIntBits(value.y);
            arguments[argumentCount++] = Float.floatToRawIntBits(value.z);
        }
    }

    /**
     * Push an object argument (serialized as MsgPack).
     */
    public void pushArg(Object value) {
        // TODO: Serialize using MsgPack and store pointer
        arguments[argumentCount++] = 0;
    }

    /**
     * Get the result as an integer.
     */
    public int getResultInt() {
        return returnCount > 0 ? (int) returnData[0] : 0;
    }

    /**
     * Get the result as a long.
     */
    public long getResultLong() {
        return returnCount > 0 ? returnData[0] : 0;
    }

    /**
     * Get the result as a float.
     */
    public float getResultFloat() {
        return returnCount > 0 ? Float.intBitsToFloat((int) returnData[0]) : 0.0f;
    }

    /**
     * Get the result as a double.
     */
    public double getResultDouble() {
        return returnCount > 0 ? Double.longBitsToDouble(returnData[0]) : 0.0;
    }

    /**
     * Get the result as a boolean.
     */
    public boolean getResultBoolean() {
        return returnCount > 0 && returnData[0] != 0;
    }

    /**
     * Get the result as a string.
     */
    public String getResultString() {
        if (returnCount == 0 || returnData[0] == 0) {
            return null;
        }
        // TODO: Convert native pointer to string
        return null;
    }

    /**
     * Get the result as a Vector3.
     */
    public Vector3 getResultVector3() {
        if (returnCount >= 3) {
            return new Vector3(
                Float.intBitsToFloat((int) returnData[0]),
                Float.intBitsToFloat((int) returnData[1]),
                Float.intBitsToFloat((int) returnData[2])
            );
        }
        return new Vector3(0, 0, 0);
    }

    /**
     * Get the result as an object (deserialized from MsgPack).
     */
    public Object getResultObject() {
        // TODO: Deserialize using MsgPack
        return null;
    }

    // Internal methods for JNI access
    long[] getArguments() {
        return arguments;
    }

    int getArgumentCount() {
        return argumentCount;
    }

    void setReturnData(long[] data, int count) {
        System.arraycopy(data, 0, returnData, 0, Math.min(count, returnData.length));
        returnCount = count;
    }

    byte[] getStringHeap() {
        return stringHeap;
    }
}
