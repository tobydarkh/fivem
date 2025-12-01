package net.citizenfx.core;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serialization and deserialization using MsgPack format.
 * This is used for cross-runtime communication and event arguments.
 */
public class MsgPackSerializer {

    /**
     * Serialize an object to MsgPack binary format.
     */
    public static byte[] serialize(Object obj) throws IOException {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            packValue(packer, obj);
            return packer.toByteArray();
        }
    }

    /**
     * Deserialize from MsgPack binary format.
     */
    public static Object deserialize(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return null;
        }

        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data)) {
            return unpackValue(unpacker);
        }
    }

    /**
     * Pack a value into MsgPack format.
     */
    private static void packValue(MessageBufferPacker packer, Object obj) throws IOException {
        if (obj == null) {
            packer.packNil();
        } else if (obj instanceof Boolean) {
            packer.packBoolean((Boolean) obj);
        } else if (obj instanceof Byte) {
            packer.packByte((Byte) obj);
        } else if (obj instanceof Short) {
            packer.packShort((Short) obj);
        } else if (obj instanceof Integer) {
            packer.packInt((Integer) obj);
        } else if (obj instanceof Long) {
            packer.packLong((Long) obj);
        } else if (obj instanceof Float) {
            packer.packFloat((Float) obj);
        } else if (obj instanceof Double) {
            packer.packDouble((Double) obj);
        } else if (obj instanceof String) {
            packer.packString((String) obj);
        } else if (obj instanceof byte[]) {
            packer.packBinaryHeader(((byte[]) obj).length);
            packer.writePayload((byte[]) obj);
        } else if (obj instanceof Vector3) {
            // Pack Vector3 as array
            Vector3 vec = (Vector3) obj;
            packer.packArrayHeader(3);
            packer.packFloat(vec.x);
            packer.packFloat(vec.y);
            packer.packFloat(vec.z);
        } else if (obj instanceof List) {
            // Pack list as array
            List<?> list = (List<?>) obj;
            packer.packArrayHeader(list.size());
            for (Object item : list) {
                packValue(packer, item);
            }
        } else if (obj instanceof Map) {
            // Pack map
            Map<?, ?> map = (Map<?, ?>) obj;
            packer.packMapHeader(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                packValue(packer, entry.getKey());
                packValue(packer, entry.getValue());
            }
        } else if (obj.getClass().isArray()) {
            // Pack array
            Object[] array = (Object[]) obj;
            packer.packArrayHeader(array.length);
            for (Object item : array) {
                packValue(packer, item);
            }
        } else {
            // Fallback: pack as nil
            packer.packNil();
        }
    }

    /**
     * Unpack a value from MsgPack format.
     */
    private static Object unpackValue(MessageUnpacker unpacker) throws IOException {
        if (!unpacker.hasNext()) {
            return null;
        }

        Value value = unpacker.unpackValue();

        if (value.isNilValue()) {
            return null;
        } else if (value.isBooleanValue()) {
            return value.asBooleanValue().getBoolean();
        } else if (value.isIntegerValue()) {
            long longValue = value.asIntegerValue().asLong();
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                return (int) longValue;
            }
            return longValue;
        } else if (value.isFloatValue()) {
            return value.asFloatValue().toDouble();
        } else if (value.isStringValue()) {
            return value.asStringValue().asString();
        } else if (value.isBinaryValue()) {
            return value.asBinaryValue().asByteArray();
        } else if (value.isArrayValue()) {
            List<Value> array = value.asArrayValue().list();
            List<Object> result = new ArrayList<>(array.size());

            // Check if this is a Vector3 (3 floats)
            if (array.size() == 3 &&
                array.get(0).isFloatValue() &&
                array.get(1).isFloatValue() &&
                array.get(2).isFloatValue()) {
                return new Vector3(
                    array.get(0).asFloatValue().toFloat(),
                    array.get(1).asFloatValue().toFloat(),
                    array.get(2).asFloatValue().toFloat()
                );
            }

            // Regular array
            for (Value item : array) {
                result.add(convertValue(item));
            }
            return result;
        } else if (value.isMapValue()) {
            Map<Value, Value> map = value.asMapValue().map();
            Map<Object, Object> result = new HashMap<>(map.size());
            for (Map.Entry<Value, Value> entry : map.entrySet()) {
                result.put(convertValue(entry.getKey()), convertValue(entry.getValue()));
            }
            return result;
        }

        return null;
    }

    /**
     * Convert a MsgPack Value to a Java object.
     */
    private static Object convertValue(Value value) {
        if (value.isNilValue()) {
            return null;
        } else if (value.isBooleanValue()) {
            return value.asBooleanValue().getBoolean();
        } else if (value.isIntegerValue()) {
            long longValue = value.asIntegerValue().asLong();
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                return (int) longValue;
            }
            return longValue;
        } else if (value.isFloatValue()) {
            return value.asFloatValue().toDouble();
        } else if (value.isStringValue()) {
            return value.asStringValue().asString();
        } else if (value.isBinaryValue()) {
            return value.asBinaryValue().asByteArray();
        } else if (value.isArrayValue()) {
            List<Value> array = value.asArrayValue().list();
            List<Object> result = new ArrayList<>(array.size());
            for (Value item : array) {
                result.add(convertValue(item));
            }
            return result;
        } else if (value.isMapValue()) {
            Map<Value, Value> map = value.asMapValue().map();
            Map<Object, Object> result = new HashMap<>(map.size());
            for (Map.Entry<Value, Value> entry : map.entrySet()) {
                result.put(convertValue(entry.getKey()), convertValue(entry.getValue()));
            }
            return result;
        }
        return null;
    }
}
