# CitizenFX JVM Scripting Runtime

This component enables gamemode development for FiveM using the Java Virtual Machine (JVM). You can write FiveM gamemodes in Java, Kotlin, Scala, or any other JVM-based language.

## Features

- **JVM-based scripting**: Write gamemodes in Java, Kotlin, Scala, or other JVM languages
- **Native invocation**: Call game natives from Java code
- **Event system**: Register and trigger events
- **Resource isolation**: Each resource runs in its own ClassLoader
- **Tickless scheduling**: Efficient cooperative scheduling system
- **Profiling support**: Integration with FiveM's profiler

## Architecture

The JVM scripting runtime follows the same architecture as the Mono (C#) runtime:

### C++ Side (Native)
- **JvmComponentHost**: Singleton managing JVM initialization, JNI method registration, and native bindings
- **JvmScriptRuntime**: Per-resource runtime instance implementing all IScript* interfaces
- **JNI Bridge**: Native methods exposed to Java for native invocation, events, etc.

### Java Side (Managed)
- **CitizenFX.Core.jar**: Core library providing the scripting API
  - `ScriptInterface`: Main bridge between C++ and Java
  - `BaseScript`: Base class for gamemodes
  - `EventManager`: Event registration and dispatching
  - `Native`: Native function invocation utilities

## Building

### Prerequisites

- JDK 11 or later
- Maven 3.6 or later
- Set `JAVA_HOME` environment variable (Windows only)

### Build Steps

1. **Build CitizenFX.Core library**:
   ```bash
   cd code/client/jvmcore
   mvn clean package
   ```

   This will create `CitizenFX.Core.jar` in `bin/citizen/jvm/lib/`

2. **Build FiveM with JVM support**:
   ```bash
   # Build FiveM as usual
   # The JVM component will be built automatically
   ```

## Usage

### Creating a JVM Gamemode

1. **Create a new Maven project**:
   ```xml
   <dependency>
       <groupId>net.citizenfx</groupId>
       <artifactId>citizenfx-core</artifactId>
       <version>2.0.0</version>
       <scope>provided</scope>
   </dependency>
   ```

2. **Extend BaseScript**:
   ```java
   package com.example;

   import net.citizenfx.core.BaseScript;

   public class MyGamemode extends BaseScript {
       @Override
       public void onLoad() {
           print("Gamemode loaded!");

           // Register event handlers
           on("playerConnecting", args -> {
               String playerName = args[0].toString();
               print("Player connecting: " + playerName);
           });

           // Call natives
           // invokeNativeVoid(0x12345678, arg1, arg2);
       }
   }
   ```

3. **Build your gamemode**:
   ```bash
   mvn clean package
   ```

4. **Create fxmanifest.lua**:
   ```lua
   fx_version 'cerulean'
   game 'gta5'

   -- Enable JVM gamemode runtime
   jvm_gamemode 'yes'

   -- Java/JAR files to load
   server_script 'target/my-gamemode.jar'
   ```

5. **Start your resource**:
   ```
   start my-gamemode
   ```

## Example

See `examples/example-gamemode/` for a complete working example.

To build and run the example:

```bash
cd code/components/citizen-scripting-jvm/examples/example-gamemode
mvn clean package
# Copy the resource to your FiveM server's resources folder
# Add 'start example-gamemode' to server.cfg
```

## API Reference

### BaseScript

Base class for all gamemodes.

**Methods**:
- `void onLoad()`: Called when the script loads
- `void onUnload()`: Called when the script unloads
- `void on(String eventName, EventHandler handler)`: Register an event handler
- `void trigger(String eventName, Object... args)`: Trigger a local event
- `void triggerServer(String eventName, Object... args)`: Trigger a server event
- `void triggerClient(Object target, String eventName, Object... args)`: Trigger a client event
- `void print(String message)`: Print to console
- `Object invokeNative(long hash, Object... args)`: Invoke a game native

### EventManager

Static event management.

**Methods**:
- `static void on(String eventName, EventHandler handler)`: Register event handler
- `static void off(String eventName, EventHandler handler)`: Unregister event handler
- `static void trigger(String eventName, Object... args)`: Trigger event
- `static void triggerServer(String eventName, Object... args)`: Trigger server event
- `static void triggerClient(Object target, String eventName, Object... args)`: Trigger client event

### Native

Static native invocation utilities.

**Methods**:
- `static Object invoke(long hash, Object... args)`: Invoke native
- `static <T> T invoke(Class<T> returnType, long hash, Object... args)`: Invoke with type
- `static void invokeVoid(long hash, Object... args)`: Invoke without return
- `static int invokeInt(long hash, Object... args)`: Invoke returning int
- `static long invokeLong(long hash, Object... args)`: Invoke returning long
- `static float invokeFloat(long hash, Object... args)`: Invoke returning float
- `static boolean invokeBoolean(long hash, Object... args)`: Invoke returning boolean
- `static String invokeString(long hash, Object... args)`: Invoke returning string

## Limitations

### Current Implementation Status

The JVM runtime is in **preview** status. The following features are implemented:

✅ **Implemented**:
- JVM initialization and management
- Resource loading (JAR files)
- Event system (registration and triggering)
- Basic native method JNI bridge
- Tickless scheduling
- Profiling integration
- Reference management (cross-runtime calls)

⚠️ **Partially Implemented**:
- Native invocation (JNI bridge exists, needs context marshaling)
- Serialization (MsgPack integration needed)
- ClassLoader isolation (currently using system classloader)

❌ **Not Yet Implemented**:
- Complete native context marshaling
- Async/await and coroutines
- Exports/externals system
- UGC assembly loading
- Debugging support

## Development

### Testing

To test the JVM runtime:

1. Build FiveM with JVM support
2. Build the example gamemode
3. Copy the example to your server's resources
4. Start the server and check console output

### Debugging

To debug Java gamemodes:

1. Add JVM debug options to `JvmComponentHost::Initialize()`:
   ```cpp
   options[n_options++].optionString = const_cast<char*>("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005");
   ```

2. Connect your IDE debugger to port 5005

### Contributing

When contributing to the JVM runtime:

1. Follow the existing code style
2. Add tests for new features
3. Update this README
4. Ensure builds succeed on both Windows and Linux

## License

See LICENSE in the root of the source tree.

## Credits

This component is modeled after `citizen-scripting-mono-v2` and follows the same architectural patterns.
