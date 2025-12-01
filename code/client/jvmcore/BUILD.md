# CitizenFX Core JVM Library - Build Instructions

## Project Structure

The library is now split into three modules for proper client/server separation:

```
jvmcore/
├── pom.xml                    (Parent POM)
├── core-shared/               (Shared classes for both client and server)
│   └── src/main/java/net/citizenfx/core/
│       ├── BaseScript.java
│       ├── ScriptInterface.java
│       ├── EventManager.java
│       ├── Scheduler.java
│       ├── ExportsManager.java
│       ├── ExternalsManager.java
│       ├── ResourceClassLoader.java
│       ├── DebugSupport.java
│       ├── MsgPackSerializer.java
│       └── Vector3.java
├── core-client/               (Client-side natives only)
│   └── src/main/java/net/citizenfx/core/client/
│       ├── Native.java
│       └── NativeContext.java
└── core-server/               (Server-side natives only)
    └── src/main/java/net/citizenfx/core/server/
        ├── Native.java
        └── NativeContext.java
```

## Prerequisites

- JDK 11 or later
- Maven 3.6 or later

## Building the Library

### 1. Build All Modules

```bash
cd code/client/jvmcore
mvn clean package
```

This will:
- Compile all three modules (shared, client, server)
- Include MsgPack and Jackson dependencies
- Create three JAR files:
  - `core-shared/target/citizenfx-core-shared-2.0.0.jar`
  - `core-client/target/CitizenFX.Core.Client-2.0.0.jar` (shaded with dependencies)
  - `core-server/target/CitizenFX.Core.Server-2.0.0.jar` (shaded with dependencies)
- Copy client and server JARs to `../../../bin/citizen/jvm/lib/`

### 2. Install to Local Maven Repository (for development)

```bash
cd code/client/jvmcore
mvn clean install
```

This installs all modules to your local Maven repository (~/.m2/repository), making them available for gamemode projects.

## Using in Gamemode Projects

### Client-Side Gamemode

For client-side gamemodes, use the client dependency:

```xml
<dependency>
    <groupId>net.citizenfx</groupId>
    <artifactId>citizenfx-core-client</artifactId>
    <version>2.0.0</version>
    <scope>provided</scope>
</dependency>
```

In your code:
```java
import net.citizenfx.core.BaseScript;
import net.citizenfx.core.client.Native;  // Client-side natives

public class MyClientGamemode extends BaseScript {
    @Override
    public void onLoad() {
        // Use client natives
        Native.invoke(0x..., args);
    }
}
```

### Server-Side Gamemode

For server-side gamemodes, use the server dependency:

```xml
<dependency>
    <groupId>net.citizenfx</groupId>
    <artifactId>citizenfx-core-server</artifactId>
    <version>2.0.0</version>
    <scope>provided</scope>
</dependency>
```

In your code:
```java
import net.citizenfx.core.BaseScript;
import net.citizenfx.core.server.Native;  // Server-side natives

public class MyServerGamemode extends BaseScript {
    @Override
    public void onLoad() {
        // Use server natives
        Native.invoke(0x..., args);
    }
}
```

### Why Separate Modules?

**Type Safety**: Prevents accidental use of client-only natives on the server (or vice versa).

Example:
- Client: `GET_PLAYER_PED()` ❌ Not available on server
- Server: `GetPlayerEndpoint()` ❌ Not available on client

By using separate dependencies, you'll get compile-time errors if you try to use the wrong natives.

## Alternative: Copy to lib/ Directory

```bash
# In your gamemode project
mkdir -p lib

# For client-side
cp /path/to/CitizenFX.Core.Client.jar lib/

# For server-side
cp /path/to/CitizenFX.Core.Server.jar lib/

# In pom.xml
<dependency>
    <groupId>net.citizenfx</groupId>
    <artifactId>citizenfx-core-client</artifactId> <!-- or core-server -->
    <version>2.0.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/CitizenFX.Core.Client.jar</systemPath>
</dependency>
```

## Automated Build Script

For convenience, use the provided build script:

```bash
# From fivem root
./code/client/jvmcore/build.sh
```

This will:
1. Build all modules
2. Install to local Maven repository
3. Copy JARs to FiveM's bin directory
4. Display success message

## Directory Structure After Build

```
fivem/
├── code/client/jvmcore/
│   ├── core-shared/
│   │   └── target/
│   │       └── citizenfx-core-shared-2.0.0.jar
│   ├── core-client/
│   │   └── target/
│   │       ├── CitizenFX.Core.Client-2.0.0.jar (shaded)
│   │       └── original-CitizenFX.Core.Client-2.0.0.jar
│   └── core-server/
│       └── target/
│           ├── CitizenFX.Core.Server-2.0.0.jar (shaded)
│           └── original-CitizenFX.Core.Server-2.0.0.jar
└── bin/citizen/jvm/lib/
    ├── CitizenFX.Core.Client.jar (runtime)
    └── CitizenFX.Core.Server.jar (runtime)
```

## Troubleshooting

### Maven not found
Install Maven:
```bash
# Ubuntu/Debian
sudo apt-get install maven

# macOS
brew install maven

# Windows
choco install maven
```

### JDK not found
Install JDK 11+:
```bash
# Ubuntu/Debian
sudo apt-get install openjdk-11-jdk

# macOS
brew install openjdk@11

# Windows
choco install openjdk11
```

### Build fails with "package does not exist"
Make sure you're building from the parent directory (`jvmcore/`), not from a submodule directory.

### Wrong natives in my project
- Using client-only natives on server? Check your dependency - it should be `citizenfx-core-server`
- Using server-only natives on client? Check your dependency - it should be `citizenfx-core-client`

## Development Workflow

1. **Make changes** to library code
2. **Rebuild**: `mvn clean install` (from `jvmcore/` directory)
3. **Rebuild gamemode**: Your gamemode will automatically pick up changes
4. **Test**: Run FiveM server/client and load your resource

## Module Dependencies

```
core-client
    └── depends on: core-shared

core-server
    └── depends on: core-shared

core-shared
    └── depends on: msgpack, jackson
```

## Publishing (Future)

To publish to Maven Central:
1. Set up GPG keys
2. Configure `~/.m2/settings.xml` with credentials
3. Run `mvn clean deploy -P release`

For now, local installation is sufficient for development.
