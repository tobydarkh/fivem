/*
 * This file is part of the CitizenFX project - http://citizen.re/
 *
 * See LICENSE and MENTIONS in the root of the source tree for information
 * regarding licensing.
 */

#include <StdInc.h>

#include <shared_mutex>

#include "JvmComponentHost.h"

#include <om/OMComponent.h>
#include <ResourceManager.h>
#include <fxScripting.h>

#include <CoreConsole.h>
#include <Error.h>
#include <Profiler.h>

#include <msgpack.hpp>

#define CITIZENFX_CORE "CitizenFX.Core"
#define CITIZENFX_GAME_NATIVE "CitizenFX." PRODUCT_NAME ".Native"

namespace fx::jvm
{
JavaVM* JvmComponentHost::s_jvm = nullptr;
jclass JvmComponentHost::s_coreClass = nullptr;
jclass JvmComponentHost::s_scriptInterfaceClass = nullptr;

decltype(JvmComponentHost::s_overrideJars) JvmComponentHost::s_overrideJars;

void JvmComponentHost::Initialize()
{
	// Create JVM
	JavaVMInitArgs vm_args;
	JavaVMOption options[10];
	int n_options = 0;

	// Set the class path to include our core JAR
	std::string classPath = "-Djava.class.path=" + MakeRelativeNarrowPath("citizen/jvm/lib/CitizenFX.Core.jar");
	options[n_options++].optionString = const_cast<char*>(classPath.c_str());

	// Additional JVM options
	options[n_options++].optionString = const_cast<char*>("-Xms64m");
	options[n_options++].optionString = const_cast<char*>("-Xmx512m");
	options[n_options++].optionString = const_cast<char*>("-XX:+UseG1GC");

	vm_args.version = JNI_VERSION_1_8;
	vm_args.nOptions = n_options;
	vm_args.options = options;
	vm_args.ignoreUnrecognized = JNI_FALSE;

	JNIEnv* env;
	jint res = JNI_CreateJavaVM(&s_jvm, (void**)&env, &vm_args);
	if (res != JNI_OK)
	{
		FatalError("Failed to create JVM (error code: %d)\n", res);
		return;
	}

	// Find ScriptInterface class
	jclass localClass = env->FindClass("net/citizenfx/core/ScriptInterface");
	if (!localClass)
	{
		env->ExceptionDescribe();
		FatalError("Could not find ScriptInterface class\n");
		return;
	}

	// Create global reference to keep class alive
	s_scriptInterfaceClass = (jclass)env->NewGlobalRef(localClass);
	env->DeleteLocalRef(localClass);

	// Register ScriptInterface native methods
	JNINativeMethod scriptInterfaceMethods[] = {
		{ const_cast<char*>("print"), const_cast<char*>("(Ljava/lang/String;Ljava/lang/String;)V"), (void*)&Java_CitizenFX_Core_ScriptInterface_Print },
		{ const_cast<char*>("getNative"), const_cast<char*>("(J)J"), (void*)&Java_CitizenFX_Core_ScriptInterface_GetNative },
		{ const_cast<char*>("invokeNative"), const_cast<char*>("(JLjava/lang/Object;J)Z"), (void*)&Java_CitizenFX_Core_ScriptInterface_InvokeNative },
		{ const_cast<char*>("cfree"), const_cast<char*>("(J)V"), (void*)&Java_CitizenFX_Core_ScriptInterface_CFree },
		{ const_cast<char*>("profilerIsRecording"), const_cast<char*>("()Z"), (void*)&Java_CitizenFX_Core_ScriptInterface_ProfilerIsRecording },
		{ const_cast<char*>("profilerEnterScope"), const_cast<char*>("(Ljava/lang/String;)V"), (void*)&Java_CitizenFX_Core_ScriptInterface_ProfilerEnterScope },
		{ const_cast<char*>("profilerExitScope"), const_cast<char*>("()V"), (void*)&Java_CitizenFX_Core_ScriptInterface_ProfilerExitScope },
		{ const_cast<char*>("canonicalizeRef"), const_cast<char*>("(JI)[B"), (void*)&Java_CitizenFX_Core_ScriptInterface_CanonicalizeRef },
		{ const_cast<char*>("invokeFunctionReference"), const_cast<char*>("(JLjava/lang/String;[B)[B"), (void*)&Java_CitizenFX_Core_ScriptInterface_InvokeFunctionReference },
		{ const_cast<char*>("readClass"), const_cast<char*>("(JLjava/lang/String;[[B)Z"), (void*)&Java_CitizenFX_Core_ScriptInterface_ReadClass }
	};

	jint methodCount = sizeof(scriptInterfaceMethods) / sizeof(scriptInterfaceMethods[0]);
	if (env->RegisterNatives(s_scriptInterfaceClass, scriptInterfaceMethods, methodCount) != JNI_OK)
	{
		env->ExceptionDescribe();
		FatalError("Failed to register ScriptInterface native methods\n");
		return;
	}

	// Find and register Native class methods
	jclass nativeClass = env->FindClass("net/citizenfx/core/Native");
	if (!nativeClass)
	{
		env->ExceptionDescribe();
		FatalError("Could not find Native class\n");
		return;
	}

	JNINativeMethod nativeMethods[] = {
		{ const_cast<char*>("getNativePointer"), const_cast<char*>("(J)J"), (void*)&Java_net_citizenfx_core_Native_getNativePointer },
		{ const_cast<char*>("invokeNativeInternal"), const_cast<char*>("(JJ[JI[J[I[B)V"), (void*)&Java_net_citizenfx_core_Native_invokeNativeInternal }
	};

	jint nativeMethodCount = sizeof(nativeMethods) / sizeof(nativeMethods[0]);
	if (env->RegisterNatives(nativeClass, nativeMethods, nativeMethodCount) != JNI_OK)
	{
		env->ExceptionDescribe();
		FatalError("Failed to register Native class methods\n");
		return;
	}

	env->DeleteLocalRef(nativeClass);

	InitializeMethods();

#ifndef IS_FXSERVER
	InitializeNativeWrapper(MakeRelativeNarrowPath("citizen/jvm/lib/"));
#endif
}

void JvmComponentHost::InitializeNativeWrapper(const std::string& platformFolder)
{
#ifndef IS_FXSERVER
	// TODO: Load native wrapper JAR for game-specific natives
	// This would be similar to loading CitizenFX.FiveM.Native.jar or CitizenFX.RedM.Native.jar
#endif
}

JNIEnv* JvmComponentHost::GetJNIEnv()
{
	if (!s_jvm)
		return nullptr;

	JNIEnv* env = nullptr;
	jint res = s_jvm->GetEnv((void**)&env, JNI_VERSION_1_8);

	if (res == JNI_EDETACHED)
	{
		// Thread not attached, attach it
		res = s_jvm->AttachCurrentThread((void**)&env, nullptr);
		if (res != JNI_OK)
		{
			return nullptr;
		}
	}
	else if (res != JNI_OK)
	{
		return nullptr;
	}

	return env;
}

void JvmComponentHost::DetachCurrentThread()
{
	if (s_jvm)
	{
		s_jvm->DetachCurrentThread();
	}
}

void JvmComponentHost::InitializeMethods()
{
	// Method initialization will be done in JvmScriptRuntime per-instance
}

jclass JvmComponentHost::LoadJarDirect(std::string_view path)
{
	JNIEnv* env = GetJNIEnv();
	if (!env)
		return nullptr;

	// TODO: Implement JAR loading using custom ClassLoader
	return nullptr;
}

void JvmComponentHost::InvokeNative(NativeHandler native, ScriptContext* context, uint64_t hash)
{
	try
	{
#ifdef IS_FXSERVER
		(*native)(*context);
#else
		native(context);
#endif
	}
	catch (const std::exception& e)
	{
		// TODO: Throw Java exception
		trace("Error executing native 0x%016llx: %s\n", hash, e.what());
	}
}

uintptr_t JvmComponentHost::GetNative(uint64_t hash)
{
#ifdef IS_FXSERVER
	auto* native = fx::ScriptEngine::GetNativeHandlerPtr(hash);
	if (native != nullptr)
		return reinterpret_cast<uintptr_t>(native);

	trace("Error acquiring native 0x%016llx, no such native found\n", hash);
	return 0;
#else
	return reinterpret_cast<uintptr_t>(rage::scrEngine::GetNativeHandler(hash));
#endif
}

void JvmComponentHost::ProfilerEnterScope(jstring name)
{
	JNIEnv* env = GetJNIEnv();
	if (!env)
		return;

	const char* nameStr = env->GetStringUTFChars(name, nullptr);
	GetProfiler()->EnterScope(nameStr);
	env->ReleaseStringUTFChars(name, nameStr);
}

// JNI method implementations
void JNICALL JvmComponentHost::Java_CitizenFX_Core_ScriptInterface_Print(JNIEnv* env, jclass cls, jstring channel, jstring text)
{
	const char* channelStr = env->GetStringUTFChars(channel, nullptr);
	const char* textStr = env->GetStringUTFChars(text, nullptr);

	console::Printf(channelStr, "%s", textStr);

	env->ReleaseStringUTFChars(channel, channelStr);
	env->ReleaseStringUTFChars(text, textStr);
}

jlong JNICALL JvmComponentHost::Java_CitizenFX_Core_ScriptInterface_GetNative(JNIEnv* env, jclass cls, jlong hash)
{
	return static_cast<jlong>(GetNative(static_cast<uint64_t>(hash)));
}

jboolean JNICALL JvmComponentHost::Java_CitizenFX_Core_ScriptInterface_InvokeNative(JNIEnv* env, jclass cls, jlong native, jobject context, jlong hash)
{
	// TODO: Implement native invocation with proper context handling
	return JNI_FALSE;
}

void JNICALL JvmComponentHost::Java_CitizenFX_Core_ScriptInterface_CFree(JNIEnv* env, jclass cls, jlong ptr)
{
	free(reinterpret_cast<void*>(ptr));
}

jboolean JNICALL JvmComponentHost::Java_CitizenFX_Core_ScriptInterface_ProfilerIsRecording(JNIEnv* env, jclass cls)
{
	return ProfilerIsRecording() ? JNI_TRUE : JNI_FALSE;
}

void JNICALL JvmComponentHost::Java_CitizenFX_Core_ScriptInterface_ProfilerEnterScope(JNIEnv* env, jclass cls, jstring name)
{
	ProfilerEnterScope(name);
}

void JNICALL JvmComponentHost::Java_CitizenFX_Core_ScriptInterface_ProfilerExitScope(JNIEnv* env, jclass cls)
{
	ProfilerExitScope();
}

jarray JNICALL JvmComponentHost::Java_CitizenFX_Core_ScriptInterface_CanonicalizeRef(JNIEnv* env, jclass cls, jlong runtime, jint refId)
{
	auto* rt = reinterpret_cast<JvmScriptRuntime*>(runtime);
	return CanonicalizeRef(rt, static_cast<int>(refId));
}

jarray JNICALL JvmComponentHost::Java_CitizenFX_Core_ScriptInterface_InvokeFunctionReference(JNIEnv* env, jclass cls, jlong runtime, jstring refId, jarray args)
{
	auto* rt = reinterpret_cast<JvmScriptRuntime*>(runtime);
	return InvokeFunctionReference(rt, refId, args);
}

jboolean JNICALL JvmComponentHost::Java_CitizenFX_Core_ScriptInterface_ReadClass(JNIEnv* env, jclass cls, jlong runtime, jstring name, jobjectArray outBytes)
{
	auto* rt = reinterpret_cast<JvmScriptRuntime*>(runtime);
	jbyteArray* bytes = reinterpret_cast<jbyteArray*>(outBytes);
	return ReadClassUGC(rt, name, bytes) ? JNI_TRUE : JNI_FALSE;
}

// Native class JNI implementations
jlong JNICALL JvmComponentHost::Java_net_citizenfx_core_Native_getNativePointer(JNIEnv* env, jclass cls, jlong hash)
{
	return static_cast<jlong>(GetNative(static_cast<uint64_t>(hash)));
}

void JNICALL JvmComponentHost::Java_net_citizenfx_core_Native_invokeNativeInternal(JNIEnv* env, jclass cls, jlong nativePtr, jlong hash,
	jlongArray args, jint argCount, jlongArray returnData, jintArray returnCount, jbyteArray stringHeap)
{
	if (nativePtr == 0)
	{
		env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Invalid native pointer");
		return;
	}

	// Get the native handler
	NativeHandler native = reinterpret_cast<NativeHandler>(nativePtr);

	// Get Java arrays
	jlong* argArray = env->GetLongArrayElements(args, nullptr);
	jlong* retArray = env->GetLongArrayElements(returnData, nullptr);
	jbyte* stringHeapArray = env->GetByteArrayElements(stringHeap, nullptr);

	try
	{
		// Create native context
#ifdef IS_FXSERVER
		fx::ScriptContext context;
#else
		rage::scrNativeCallContext context;
#endif

		// Copy arguments to context
		// Arguments are stored as 64-bit values
		uint64_t* contextArgs = new uint64_t[argCount];
		for (int i = 0; i < argCount; i++)
		{
			contextArgs[i] = static_cast<uint64_t>(argArray[i]);
		}

		// Initialize context
#ifdef IS_FXSERVER
		context.SetArgumentBuffer(contextArgs, argCount);
#else
		// For client, we need to set up the rage context
		for (int i = 0; i < argCount; i++)
		{
			context.Push(contextArgs[i]);
		}
#endif

		// Invoke the native
		InvokeNative(native, &context, static_cast<uint64_t>(hash));

		// Get return values
#ifdef IS_FXSERVER
		int numResults = context.GetArgumentCount();
		for (int i = 0; i < numResults && i < 32; i++)
		{
			retArray[i] = static_cast<jlong>(context.GetArgument<uint64_t>(i));
		}
#else
		// For client (rage context)
		int numResults = 1; // Most natives return one value
		if (numResults > 0)
		{
			retArray[0] = static_cast<jlong>(*context.GetArgumentBuffer());
		}
#endif

		// Set return count
		jint retCountValue = numResults;
		env->SetIntArrayRegion(returnCount, 0, 1, &retCountValue);

		delete[] contextArgs;
	}
	catch (const std::exception& e)
	{
		env->ReleaseByteArrayElements(stringHeap, stringHeapArray, 0);
		env->ReleaseLongArrayElements(returnData, retArray, 0);
		env->ReleaseLongArrayElements(args, argArray, 0);

		std::string errorMsg = fmt::sprintf("Error invoking native 0x%016llx: %s", hash, e.what());
		env->ThrowNew(env->FindClass("java/lang/RuntimeException"), errorMsg.c_str());
		return;
	}

	// Release arrays
	env->ReleaseByteArrayElements(stringHeap, stringHeapArray, 0);
	env->ReleaseLongArrayElements(returnData, retArray, 0);
	env->ReleaseLongArrayElements(args, argArray, 0);
}
}

static InitFunction initFunction([] ()
{
	fx::ResourceManager::OnInitializeInstance.Connect([](fx::ResourceManager* instance)
	{
		// JVM threads are managed differently, but we ensure env is available per-thread
		instance->OnTick.Connect([]()
		{
			fx::jvm::JvmComponentHost::GetJNIEnv();
		}, INT32_MIN);
	});

	fx::jvm::JvmComponentHost::Initialize();
});
