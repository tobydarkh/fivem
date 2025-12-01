package net.citizenfx.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Custom ClassLoader for loading JARs and classes from FiveM resources.
 * Provides isolation between resources and supports dynamic loading.
 */
public class ResourceClassLoader extends SecureClassLoader {
    private final String resourceName;
    private final Map<String, byte[]> classBytes = new HashMap<>();
    private final List<URL> jarUrls = new ArrayList<>();

    public ResourceClassLoader(String resourceName, ClassLoader parent) {
        super(parent);
        this.resourceName = resourceName;
    }

    /**
     * Load a JAR file into this class loader.
     *
     * @param jarPath Path to the JAR file
     * @throws IOException If loading fails
     */
    public void loadJar(String jarPath) throws IOException {
        Path path = Paths.get(jarPath);
        if (!Files.exists(path)) {
            throw new IOException("JAR file not found: " + jarPath);
        }

        // Read JAR entries
        try (JarInputStream jis = new JarInputStream(Files.newInputStream(path))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (entry.getName().endsWith(".class")) {
                    // Read class bytes
                    byte[] bytes = readAllBytes(jis);
                    String className = entry.getName()
                        .replace('/', '.')
                        .substring(0, entry.getName().length() - 6); // Remove .class
                    classBytes.put(className, bytes);
                }
            }
        }

        // Add JAR URL for resource loading
        jarUrls.add(path.toUri().toURL());

        ScriptInterface.printMessage("script",
            String.format("Loaded JAR: %s (%d classes)", jarPath, classBytes.size()));
    }

    /**
     * Load a class from raw bytes (for UGC content).
     *
     * @param className Class name
     * @param bytes Class bytes
     */
    public void loadClassBytes(String className, byte[] bytes) {
        classBytes.put(className, bytes);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // Check if we have the class bytes
        byte[] bytes = classBytes.get(name);
        if (bytes != null) {
            return defineClass(name, bytes, 0, bytes.length);
        }

        // Not found
        throw new ClassNotFoundException(name);
    }

    @Override
    protected URL findResource(String name) {
        // Try to find resource in loaded JARs
        for (URL jarUrl : jarUrls) {
            try {
                URL resourceUrl = new URL("jar:" + jarUrl + "!/" + name);
                if (resourceUrl.openConnection().getContentLength() > 0) {
                    return resourceUrl;
                }
            } catch (IOException e) {
                // Continue searching
            }
        }
        return null;
    }

    /**
     * Get the resource name this class loader belongs to.
     */
    public String getResourceName() {
        return resourceName;
    }

    /**
     * Find and instantiate the main class from a loaded JAR.
     *
     * @return Main class instance, or null if not found
     */
    public Object findAndInstantiateMainClass() {
        // Look for classes extending BaseScript
        for (String className : classBytes.keySet()) {
            try {
                Class<?> clazz = loadClass(className);
                if (BaseScript.class.isAssignableFrom(clazz) &&
                    !clazz.isInterface() &&
                    !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                    // Found a BaseScript subclass, instantiate it
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    ScriptInterface.printMessage("script",
                        "Instantiated main class: " + className);
                    return instance;
                }
            } catch (Exception e) {
                // Skip this class
            }
        }

        return null;
    }

    /**
     * Read all bytes from an input stream.
     */
    private byte[] readAllBytes(java.io.InputStream is) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}
