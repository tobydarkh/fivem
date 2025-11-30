fx_version 'cerulean'
game 'gta5'

author 'Example Author'
description 'Example JVM Gamemode for FiveM'
version '1.0.0'

-- Enable JVM gamemode runtime
jvm_gamemode 'yes'

-- Java/JAR files to load
files {
    'target/example-gamemode.jar'
}

-- Server scripts (JAR files)
server_script 'target/example-gamemode.jar'
