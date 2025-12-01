# CitizenFX Core JVM Library - Build Instructions

## Prerequisites

- JDK 11 or later
- Maven 3.6 or later

## Building the Library

### 1. Build CitizenFX.Core.jar

```bash
cd code/client/jvmcore
mvn clean package
```

This will:
- Compile all Java sources
- Include MsgPack and Jackson dependencies
- Create `target/CitizenFX.Core-2.0.0.jar` (shaded JAR with dependencies)
- Copy to `../../../bin/citizen/jvm/lib/CitizenFX.Core.jar`

### 2. Install to Local Maven Repository (for development)

```bash
cd code/client/jvmcore
mvn clean install
```

This installs the library to your local Maven repository (~/.m2/repository), making it available for gamemode projects.

## Using in Gamemode Projects

### Option 1: Local Maven Dependency (Recommended for Development)

After running `mvn install`, add to your gamemode's `pom.xml`:

```xml
<dependency>
    <groupId>net.citizenfx</groupId>
    <artifactId>citizenfx-core</artifactId>
    <version>2.0.0</version>
    <scope>provided</scope>
</dependency>
```

### Option 2: System Dependency (Alternative)

If you haven't installed to Maven, reference the JAR directly:

```xml
<dependency>
    <groupId>net.citizenfx</groupId>
    <artifactId>citizenfx-core</artifactId>
    <version>2.0.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/../path/to/CitizenFX.Core.jar</systemPath>
</dependency>
```

### Option 3: Copy to lib/ Directory

```bash
# In your gamemode project
mkdir -p lib
cp /path/to/CitizenFX.Core.jar lib/

# In pom.xml
<dependency>
    <groupId>net.citizenfx</groupId>
    <artifactId>citizenfx-core</artifactId>
    <version>2.0.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/CitizenFX.Core.jar</systemPath>
</dependency>
```

## Automated Build Script

For convenience, use the provided build script:

```bash
# From fivem root
./code/client/jvmcore/build.sh
```

This will:
1. Build the library
2. Install to local Maven repository
3. Copy to FiveM's bin directory
4. Display success message

## Directory Structure After Build

```
fivem/
├── code/client/jvmcore/
│   ├── target/
│   │   ├── CitizenFX.Core-2.0.0.jar (shaded)
│   │   └── original-CitizenFX.Core-2.0.0.jar
│   └── pom.xml
└── bin/citizen/jvm/lib/
    └── CitizenFX.Core.jar (runtime)
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
Make sure all Java files are in the correct package structure:
```
src/main/java/net/citizenfx/core/
├── BaseScript.java
├── ScriptInterface.java
├── Native.java
└── ...
```

## Development Workflow

1. **Make changes** to library code
2. **Rebuild**: `mvn clean install`
3. **Rebuild gamemode**: Your gamemode will automatically pick up changes
4. **Test**: Run FiveM server and load your resource

## Publishing (Future)

To publish to Maven Central:
1. Set up GPG keys
2. Configure `~/.m2/settings.xml` with credentials
3. Run `mvn clean deploy -P release`

For now, local installation is sufficient for development.
