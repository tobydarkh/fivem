#pragma once

#include <StdInc.h>

#include <string>
#include <string_view>
#include <unordered_map>
#include <jni.h>

#include "JvmScriptRuntime.h"

#ifndef IS_FXSERVER
#include <scrEngine.h>
typedef rage::scrEngine::NativeHandler NativeHandler;
typedef rage::scrNativeCallContext ScriptContext;
#else
#include <ScriptEngine.h>
typedef fx::TNativeHandler* NativeHandler;
typedef fx::ScriptContext ScriptContext;
#endif

namespace fx::jvm
{
class JvmComponentHost
{
public:
	enum class JarOverrideRule : uint8_t
	{
		FULL_VERSION_EQUAL,

		MAJOR_VERSION_EQUAL,
		MAJOR_VERSION_GREATER_OR_EQUAL,
		MAJOR_VERSION_LESSER_OR_EQUAL
	};

	typedef std::tuple<jclass, std::array<uint16_t, 4>, JarOverrideRule> JarOverride;

private:
	static JavaVM* s_jvm;
	static jclass s_coreClass;
	static jclass s_scriptInterfaceClass;

	static std::unordered_map<std::string, std::vector<JarOverride>> s_overrideJars;

	static fwRefContainer<fx::ProfilerComponent> GetProfiler()
	{
		static auto s_profiler = fx::ResourceManager::GetCurrent()->GetComponent<fx::ProfilerComponent>();
		return s_profiler;
	}

public:
	// Initializes the JVM environment, must be called before making and using script runtimes
	static void Initialize();

	// Client only, loads native wrapper JAR and adds it to the overrides
	static void InitializeNativeWrapper(const std::string& platformFolder);

	// Gets a JNIEnv for the current thread, attaching if necessary
	static JNIEnv* GetJNIEnv();

	// Detaches the current thread from the JVM
	static void DetachCurrentThread();

	// Get JVM instance
	static JavaVM* GetJVM();

#pragma region JAR/Class related

public:
	// Get core script interface class
	static jclass GetScriptInterfaceClass();

	// Get all JAR overrides by the given name, will be nullptr if it's not present
	static const std::vector<JarOverride>* GetJarOverrides(std::string_view jarName);

private:
	// Load JAR/class contents, used for UGC scripts
	static bool ReadClassUGC(JvmScriptRuntime* runtime, jstring name, jbyteArray* classBytes);

	/// Loads a JAR directly
	static jclass LoadJarDirect(std::string_view path);

	// Add a JAR that'll take precedence when the JVM tries to load them
	static void AddJarOverride(std::string_view jarName, jclass cls, const std::array<uint16_t, 4>& version, JarOverrideRule rule);

#pragma endregion

#pragma region Profiler

public:
	static bool ProfilerIsRecording();

	static void ProfilerEnterScope(jstring name);

	static void ProfilerExitScope();

#pragma endregion

#pragma region Natives

private:
	static void InvokeNative(NativeHandler native, ScriptContext* context, uint64_t hash);

	static uintptr_t GetNative(uint64_t hash);

#pragma endregion

private:
	static jarray CanonicalizeRef(const JvmScriptRuntime* runTime, int referenceId);

	static jarray InvokeFunctionReference(const JvmScriptRuntime* runTime, jstring referenceId, jarray argsSerialized);

private:
	static void InitializeMethods();

	// JNI method implementations (called from Java)
	static void JNICALL Java_CitizenFX_Core_ScriptInterface_Print(JNIEnv* env, jclass cls, jstring channel, jstring text);
	static jlong JNICALL Java_CitizenFX_Core_ScriptInterface_GetNative(JNIEnv* env, jclass cls, jlong hash);
	static jboolean JNICALL Java_CitizenFX_Core_ScriptInterface_InvokeNative(JNIEnv* env, jclass cls, jlong native, jobject context, jlong hash);
	static void JNICALL Java_CitizenFX_Core_ScriptInterface_CFree(JNIEnv* env, jclass cls, jlong ptr);
	static jboolean JNICALL Java_CitizenFX_Core_ScriptInterface_ProfilerIsRecording(JNIEnv* env, jclass cls);
	static void JNICALL Java_CitizenFX_Core_ScriptInterface_ProfilerEnterScope(JNIEnv* env, jclass cls, jstring name);
	static void JNICALL Java_CitizenFX_Core_ScriptInterface_ProfilerExitScope(JNIEnv* env, jclass cls);
	static jarray JNICALL Java_CitizenFX_Core_ScriptInterface_CanonicalizeRef(JNIEnv* env, jclass cls, jlong runtime, jint refId);
	static jarray JNICALL Java_CitizenFX_Core_ScriptInterface_InvokeFunctionReference(JNIEnv* env, jclass cls, jlong runtime, jstring refId, jarray args);
	static jboolean JNICALL Java_CitizenFX_Core_ScriptInterface_ReadClass(JNIEnv* env, jclass cls, jlong runtime, jstring name, jobjectArray outBytes);
};

inline JavaVM* JvmComponentHost::GetJVM()
{
	return s_jvm;
}

inline jclass JvmComponentHost::GetScriptInterfaceClass()
{
	return s_scriptInterfaceClass;
}

inline bool JvmComponentHost::ProfilerIsRecording()
{
	return GetProfiler()->IsRecording();
}

inline void JvmComponentHost::ProfilerExitScope()
{
	GetProfiler()->ExitScope();
}

inline bool JvmComponentHost::ReadClassUGC(JvmScriptRuntime* runtime, jstring name, jbyteArray* classBytes)
{
	return runtime->ReadClass(name, classBytes);
}

inline const std::vector<JvmComponentHost::JarOverride>* JvmComponentHost::GetJarOverrides(std::string_view jarName)
{
	auto found = s_overrideJars.find(std::string(jarName));
	return found != s_overrideJars.end() ? &found->second : nullptr;
}

inline void JvmComponentHost::AddJarOverride(std::string_view jarName, jclass cls,
	const std::array<uint16_t, 4>& version, JvmComponentHost::JarOverrideRule rule)
{
	s_overrideJars[std::string(jarName)].emplace_back(cls, version, rule);
}

inline jarray JvmComponentHost::CanonicalizeRef(const JvmScriptRuntime* runTime, int referenceId)
{
	return runTime->CanonicalizeRef(referenceId);
}

inline jarray JvmComponentHost::InvokeFunctionReference(const JvmScriptRuntime* runTime, jstring referenceId, jarray argsSerialized)
{
	return runTime->InvokeFunctionReference(referenceId, argsSerialized);
}
}
