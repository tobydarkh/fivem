local cwd = os.getcwd()

return function()
	filter {}

	add_dependencies { 'vendor:eastl' }

	if os.istarget('windows') then
		-- On Windows, we expect JAVA_HOME to be set
		local javaHome = os.getenv("JAVA_HOME")
		if javaHome then
			includedirs { javaHome .. "/include/", javaHome .. "/include/win32/" }

			filter 'architecture:x64'
				libdirs { javaHome .. "/lib/" }
				links { "jvm" }

			filter 'architecture:x86'
				libdirs { javaHome .. "/lib/" }
				links { "jvm" }

			filter {}
		else
			error("JAVA_HOME environment variable not set. Please set it to your JDK installation directory.")
		end
	else
		-- On Linux, try to find JDK installation
		local jvmPaths = {
			"/usr/lib/jvm/java-17-openjdk-amd64/",
			"/usr/lib/jvm/java-11-openjdk-amd64/",
			"/usr/lib/jvm/java-21-openjdk-amd64/",
			"/usr/lib/jvm/default-java/",
			"/usr/lib/jvm/default/"
		}

		local jvmFound = false
		for _, path in ipairs(jvmPaths) do
			if os.isdir(path) then
				includedirs { path .. "include/", path .. "include/linux/" }
				libdirs { path .. "lib/server/" }
				jvmFound = true
				break
			end
		end

		if not jvmFound then
			-- Fallback to system paths
			includedirs { "/usr/include/java/", "/usr/include/" }
		end

		links { "jvm" }
	end
end
