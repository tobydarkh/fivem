package net.citizenfx.core;

/**
 * Represents a 3D vector.
 */
public class Vector3 {
    public final float x;
    public final float y;
    public final float z;

    public Vector3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3() {
        this(0, 0, 0);
    }

    /**
     * Calculate the length of this vector.
     */
    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * Calculate the squared length of this vector.
     */
    public float lengthSquared() {
        return x * x + y * y + z * z;
    }

    /**
     * Normalize this vector.
     */
    public Vector3 normalize() {
        float len = length();
        if (len > 0) {
            return new Vector3(x / len, y / len, z / len);
        }
        return this;
    }

    /**
     * Add another vector to this vector.
     */
    public Vector3 add(Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    /**
     * Subtract another vector from this vector.
     */
    public Vector3 subtract(Vector3 other) {
        return new Vector3(x - other.x, y - other.y, z - other.z);
    }

    /**
     * Multiply this vector by a scalar.
     */
    public Vector3 multiply(float scalar) {
        return new Vector3(x * scalar, y * scalar, z * scalar);
    }

    /**
     * Divide this vector by a scalar.
     */
    public Vector3 divide(float scalar) {
        if (scalar != 0) {
            return new Vector3(x / scalar, y / scalar, z / scalar);
        }
        return this;
    }

    /**
     * Calculate the dot product with another vector.
     */
    public float dot(Vector3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    /**
     * Calculate the cross product with another vector.
     */
    public Vector3 cross(Vector3 other) {
        return new Vector3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        );
    }

    /**
     * Calculate the distance to another vector.
     */
    public float distanceTo(Vector3 other) {
        return subtract(other).length();
    }

    /**
     * Calculate the squared distance to another vector.
     */
    public float distanceToSquared(Vector3 other) {
        return subtract(other).lengthSquared();
    }

    @Override
    public String toString() {
        return String.format("Vector3(%.2f, %.2f, %.2f)", x, y, z);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Vector3)) return false;
        Vector3 other = (Vector3) obj;
        return Float.compare(x, other.x) == 0 &&
               Float.compare(y, other.y) == 0 &&
               Float.compare(z, other.z) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(x);
        result = 31 * result + Float.hashCode(y);
        result = 31 * result + Float.hashCode(z);
        return result;
    }
}
