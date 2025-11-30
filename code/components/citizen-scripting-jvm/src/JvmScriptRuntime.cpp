/*
 * This file is part of the CitizenFX project - http://citizen.re/
 *
 * See LICENSE and MENTIONS in the root of the source tree for information
 * regarding licensing.
 */

#include "StdInc.h"
#include "JvmScriptRuntime.h"

#include "JvmComponentHost.h"

#include "fxScriptBuffer.h"

#include <msgpack.hpp>
#include <Profiler.h>

#include <om/OMPtr.h>

using namespace std::literals;

uint64_t GetCurrentSchedulerTime()
{
	return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

bool IsProfiling()
{
	static auto profiler = fx::ResourceManager::GetCurrent()->GetComponent<fx::ProfilerComponent>();
	return profiler->IsRecording();
}

namespace fx::jvm
{

JvmScriptRuntime::~JvmScriptRuntime()
{
	if (m_classLoader)
	{
		JNIEnv* env = JvmComponentHost::GetJNIEnv();
		if (env)
		{
			env->DeleteGlobalRef(m_classLoader);
			if (m_scriptInterfaceClass)
				env->DeleteGlobalRef(m_scriptInterfaceClass);
		}
	}
}

result_t JvmScriptRuntime::Create(IScriptHost* host)
{
	try
	{
		m_scriptHost = host;

		assert(FX_SUCCEEDED(fx::MakeInterface(&m_handler, CLSID_ScriptRuntimeHandler)));

		{
			fx::OMPtr<IScriptHost> ptr(host);

			fx::OMPtr<IScriptHostWithResourceData> resourcePtr;
			ptr.As(&resourcePtr);
			m_resourceHost = resourcePtr.GetRef();

			fx::OMPtr<IScriptHostWithManifest> manifestPtr;
			ptr.As(&manifestPtr);
			m_manifestHost = manifestPtr.GetRef();
		}

		char* resourceName = nullptr;
		m_resourceHost->GetResourceName(&resourceName);
		m_resourceName = resourceName;

		fx::PushEnvironment env(this);

		JNIEnv* jniEnv = JvmComponentHost::GetJNIEnv();
		if (!jniEnv)
		{
			trace("Failed to get JNI environment\n");
			return FX_E_INVALIDARG;
		}

		fx::ResourceManager* resourceManager = fx::ResourceManager::GetCurrent();
		fwRefContainer<fx::Resource> resource = resourceManager->GetResource(resourceName);
		std::string resourcePath = resource->GetPath();

		// Create a custom ClassLoader for this resource
		// TODO: Implement custom URLClassLoader for resource isolation
		jclass classLoaderClass = jniEnv->FindClass("java/net/URLClassLoader");
		if (!classLoaderClass)
		{
			trace("Failed to find URLClassLoader class\n");
			return FX_E_INVALIDARG;
		}

		// For now, use the system class loader
		jclass systemClass = jniEnv->FindClass("java/lang/ClassLoader");
		jmethodID getSystemClassLoader = jniEnv->GetStaticMethodID(systemClass, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
		jobject localClassLoader = jniEnv->CallStaticObjectMethod(systemClass, getSystemClassLoader);

		if (jniEnv->ExceptionCheck())
		{
			jniEnv->ExceptionDescribe();
			jniEnv->ExceptionClear();
			return FX_E_INVALIDARG;
		}

		m_classLoader = jniEnv->NewGlobalRef(localClassLoader);
		jniEnv->DeleteLocalRef(localClassLoader);

		// Get ScriptInterface class
		jclass localScriptInterface = jniEnv->FindClass("net/citizenfx/core/ScriptInterface");
		if (!localScriptInterface)
		{
			trace("Failed to find ScriptInterface class\n");
			return FX_E_INVALIDARG;
		}

		m_scriptInterfaceClass = (jclass)jniEnv->NewGlobalRef(localScriptInterface);
		jniEnv->DeleteLocalRef(localScriptInterface);

		InitializeMethods(jniEnv);

		// Call Initialize method
		jmethodID initMethod = jniEnv->GetStaticMethodID(m_scriptInterfaceClass, "initialize",
			"(Ljava/lang/String;JIJ)V");

		if (!initMethod)
		{
			trace("Failed to find initialize method\n");
			return FX_E_INVALIDARG;
		}

		jstring jResourceName = jniEnv->NewStringUTF(resourceName);
		jniEnv->CallStaticVoidMethod(m_scriptInterfaceClass, initMethod,
			jResourceName,
			reinterpret_cast<jlong>(this),
			static_cast<jint>(m_instanceId),
			reinterpret_cast<jlong>(&m_sharedData));

		jniEnv->DeleteLocalRef(jResourceName);

		if (jniEnv->ExceptionCheck())
		{
			jniEnv->ExceptionDescribe();
			jniEnv->ExceptionClear();
			return FX_E_INVALIDARG;
		}

		return FX_S_OK;
	}
	catch (std::exception& e)
	{
		trace("Exception in JvmScriptRuntime::Create: %s\n", e.what());
	}

	return FX_E_INVALIDARG;
}

void JvmScriptRuntime::InitializeMethods(JNIEnv* env)
{
	m_tickMethod = env->GetStaticMethodID(m_scriptInterfaceClass, "tick", "(JZ)V");
	m_triggerEventMethod = env->GetStaticMethodID(m_scriptInterfaceClass, "triggerEvent",
		"(Ljava/lang/String;[BLjava/lang/String;JZ)V");
	m_loadClass = env->GetStaticMethodID(m_scriptInterfaceClass, "loadClass",
		"(Ljava/lang/String;)V");

	m_callRefMethod = env->GetStaticMethodID(m_scriptInterfaceClass, "callRef", "(I[BJZ)[B");
	m_duplicateRefMethod = env->GetStaticMethodID(m_scriptInterfaceClass, "duplicateRef", "(I)I");
	m_removeRefMethod = env->GetStaticMethodID(m_scriptInterfaceClass, "removeRef", "(I)V");

	m_getMemoryUsageMethod = env->GetStaticMethodID(m_scriptInterfaceClass, "getMemoryUsage", "()J");
	m_startProfilingMethod = env->GetStaticMethodID(m_scriptInterfaceClass, "startProfiling", "()V");
	m_stopProfilingMethod = env->GetStaticMethodID(m_scriptInterfaceClass, "stopProfiling", "()V");
}

result_t JvmScriptRuntime::Destroy()
{
	JNIEnv* env = JvmComponentHost::GetJNIEnv();
	if (env && m_classLoader)
	{
		env->DeleteGlobalRef(m_classLoader);
		m_classLoader = nullptr;

		if (m_scriptInterfaceClass)
		{
			env->DeleteGlobalRef(m_scriptInterfaceClass);
			m_scriptInterfaceClass = nullptr;
		}
	}

	m_scriptHost = nullptr;

	return FX_S_OK;
}

result_t JvmScriptRuntime::Tick()
{
	// Tickless scheduling
	{
		auto nextScheduledTime = m_sharedData.m_scheduledTime.load();
		if (GetCurrentSchedulerTime() < nextScheduledTime)
		{
			return FX_S_OK;
		}

		m_sharedData.m_scheduledTime.store(~uint64_t(0));
	}

	m_handler->PushRuntime(static_cast<IScriptRuntime*>(this));
	if (m_parentObject)
		m_parentObject->OnActivate();

	JNIEnv* env = JvmComponentHost::GetJNIEnv();
	if (!env || !m_tickMethod)
	{
		if (m_parentObject)
			m_parentObject->OnDeactivate();
		m_handler->PopRuntime(static_cast<IScriptRuntime*>(this));
		return FX_E_INVALIDARG;
	}

	env->CallStaticVoidMethod(m_scriptInterfaceClass, m_tickMethod,
		static_cast<jlong>(GetCurrentSchedulerTime()),
		IsProfiling() ? JNI_TRUE : JNI_FALSE);

	if (env->ExceptionCheck())
	{
		env->ExceptionDescribe();
		env->ExceptionClear();
	}

	m_handler->PopRuntime(static_cast<IScriptRuntime*>(this));
	if (m_parentObject)
		m_parentObject->OnDeactivate();

	return FX_S_OK;
}

result_t JvmScriptRuntime::TriggerEvent(char* eventName, char* argsSerialized, uint32_t serializedSize, char* sourceId)
{
	fx::PushEnvironment env(this);

	JNIEnv* jniEnv = JvmComponentHost::GetJNIEnv();
	if (!jniEnv || !m_triggerEventMethod)
	{
		return FX_E_INVALIDARG;
	}

	jstring jEventName = jniEnv->NewStringUTF(eventName);
	jstring jSourceId = jniEnv->NewStringUTF(sourceId);

	jbyteArray jArgs = jniEnv->NewByteArray(serializedSize);
	jniEnv->SetByteArrayRegion(jArgs, 0, serializedSize, reinterpret_cast<jbyte*>(argsSerialized));

	jniEnv->CallStaticVoidMethod(m_scriptInterfaceClass, m_triggerEventMethod,
		jEventName, jArgs, jSourceId,
		static_cast<jlong>(GetCurrentSchedulerTime()),
		IsProfiling() ? JNI_TRUE : JNI_FALSE);

	jniEnv->DeleteLocalRef(jEventName);
	jniEnv->DeleteLocalRef(jSourceId);
	jniEnv->DeleteLocalRef(jArgs);

	if (jniEnv->ExceptionCheck())
	{
		jniEnv->ExceptionDescribe();
		jniEnv->ExceptionClear();
		return FX_E_INVALIDARG;
	}

	return FX_S_OK;
}

void* JvmScriptRuntime::GetParentObject()
{
	return m_parentObject;
}

void JvmScriptRuntime::SetParentObject(void* ptr)
{
	m_parentObject = reinterpret_cast<fx::Resource*>(ptr);
}

int JvmScriptRuntime::GetInstanceId()
{
	return m_instanceId;
}

int JvmScriptRuntime::HandlesFile(char* filename, IScriptHostWithResourceData* metadata)
{
	int jvmFlagCount = 0;
	metadata->GetNumResourceMetaData(const_cast<char*>("jvm_gamemode"), &jvmFlagCount);

	// check if jvm_gamemode has been set
	if (jvmFlagCount == 0)
	{
		return false;
	}

	// check if file ends with .jar
	size_t size = strlen(filename);
	if (size <= 4 || strncmp(filename + size - 4, ".jar", 4) != 0)
	{
		return false;
	}

	// Get the value of jvm_gamemode flag
	char* flagValue = nullptr;
	metadata->GetResourceMetaData(const_cast<char*>("jvm_gamemode"), 0, &flagValue);

	if (!flagValue || strcmp(flagValue, "yes") != 0)
	{
		console::PrintError(_CFX_NAME_STRING(_CFX_COMPONENT_NAME),
			"jvm_gamemode flag must be set to 'yes' to load JVM gamemodes, skipped loading %s.\n", filename);
		return false;
	}

	return true;
}

result_t JvmScriptRuntime::LoadFile(char* scriptFile)
{
	fx::PushEnvironment env(this);

	JNIEnv* jniEnv = JvmComponentHost::GetJNIEnv();
	if (!jniEnv || !m_loadClass)
	{
		return FX_E_INVALIDARG;
	}

	// Get resource path
	fx::ResourceManager* resourceManager = fx::ResourceManager::GetCurrent();
	fwRefContainer<fx::Resource> resource = resourceManager->GetResource(m_resourceName.c_str());
	std::string fullPath = resource->GetPath() + "/" + scriptFile;

	jstring jPath = jniEnv->NewStringUTF(fullPath.c_str());
	jniEnv->CallStaticVoidMethod(m_scriptInterfaceClass, m_loadClass, jPath);
	jniEnv->DeleteLocalRef(jPath);

	if (jniEnv->ExceptionCheck())
	{
		jniEnv->ExceptionDescribe();
		jniEnv->ExceptionClear();
		return FX_E_INVALIDARG;
	}

	return FX_S_OK;
}

result_t JvmScriptRuntime::CallRef(int32_t refIdx, char* argsSerialized, uint32_t argsSize, char** retval, uint32_t* retvalLength)
{
	fx::PushEnvironment env(this);

	JNIEnv* jniEnv = JvmComponentHost::GetJNIEnv();
	if (!jniEnv || !m_callRefMethod)
	{
		return FX_E_INVALIDARG;
	}

	jbyteArray jArgs = jniEnv->NewByteArray(argsSize);
	jniEnv->SetByteArrayRegion(jArgs, 0, argsSize, reinterpret_cast<jbyte*>(argsSerialized));

	jbyteArray result = (jbyteArray)jniEnv->CallStaticObjectMethod(m_scriptInterfaceClass, m_callRefMethod,
		static_cast<jint>(refIdx), jArgs,
		static_cast<jlong>(GetCurrentSchedulerTime()),
		IsProfiling() ? JNI_TRUE : JNI_FALSE);

	jniEnv->DeleteLocalRef(jArgs);

	if (jniEnv->ExceptionCheck())
	{
		jniEnv->ExceptionDescribe();
		jniEnv->ExceptionClear();
		return FX_E_INVALIDARG;
	}

	if (result)
	{
		jsize len = jniEnv->GetArrayLength(result);
		*retvalLength = len;
		*retval = (char*)malloc(len);
		jniEnv->GetByteArrayRegion(result, 0, len, reinterpret_cast<jbyte*>(*retval));
		jniEnv->DeleteLocalRef(result);
	}
	else
	{
		*retvalLength = 0;
		*retval = nullptr;
	}

	return FX_S_OK;
}

result_t JvmScriptRuntime::DuplicateRef(int32_t refIdx, int32_t* outRefIdx)
{
	fx::PushEnvironment env(this);

	JNIEnv* jniEnv = JvmComponentHost::GetJNIEnv();
	if (!jniEnv || !m_duplicateRefMethod)
	{
		return FX_E_INVALIDARG;
	}

	jint result = jniEnv->CallStaticIntMethod(m_scriptInterfaceClass, m_duplicateRefMethod,
		static_cast<jint>(refIdx));

	if (jniEnv->ExceptionCheck())
	{
		jniEnv->ExceptionDescribe();
		jniEnv->ExceptionClear();
		return FX_E_INVALIDARG;
	}

	*outRefIdx = static_cast<int32_t>(result);

	return FX_S_OK;
}

result_t JvmScriptRuntime::RemoveRef(int32_t refIdx)
{
	fx::PushEnvironment env(this);

	JNIEnv* jniEnv = JvmComponentHost::GetJNIEnv();
	if (!jniEnv || !m_removeRefMethod)
	{
		return FX_E_INVALIDARG;
	}

	jniEnv->CallStaticVoidMethod(m_scriptInterfaceClass, m_removeRefMethod,
		static_cast<jint>(refIdx));

	if (jniEnv->ExceptionCheck())
	{
		jniEnv->ExceptionDescribe();
		jniEnv->ExceptionClear();
		return FX_E_INVALIDARG;
	}

	return FX_S_OK;
}

result_t JvmScriptRuntime::RequestMemoryUsage()
{
	// Trigger memory usage calculation
	return FX_S_OK;
}

result_t JvmScriptRuntime::GetMemoryUsage(int64_t* memoryUsage)
{
	JNIEnv* jniEnv = JvmComponentHost::GetJNIEnv();
	if (!jniEnv || !m_getMemoryUsageMethod)
	{
		*memoryUsage = 0;
		return FX_E_INVALIDARG;
	}

	jlong result = jniEnv->CallStaticLongMethod(m_scriptInterfaceClass, m_getMemoryUsageMethod);

	if (jniEnv->ExceptionCheck())
	{
		jniEnv->ExceptionDescribe();
		jniEnv->ExceptionClear();
		*memoryUsage = 0;
		return FX_E_INVALIDARG;
	}

	*memoryUsage = static_cast<int64_t>(result);

	return FX_S_OK;
}

result_t JvmScriptRuntime::SetDebugEventListener(IDebugEventListener* listener)
{
	m_debugListener = listener;
	return FX_S_OK;
}

result_t JvmScriptRuntime::SetScriptIdentifier(char* fileName, int32_t scriptId)
{
	m_scriptIds[fileName] = scriptId;
	return FX_S_OK;
}

result_t JvmScriptRuntime::SetupFxProfiler()
{
	JNIEnv* jniEnv = JvmComponentHost::GetJNIEnv();
	if (!jniEnv || !m_startProfilingMethod)
	{
		return FX_E_INVALIDARG;
	}

	jniEnv->CallStaticVoidMethod(m_scriptInterfaceClass, m_startProfilingMethod);

	if (jniEnv->ExceptionCheck())
	{
		jniEnv->ExceptionDescribe();
		jniEnv->ExceptionClear();
		return FX_E_INVALIDARG;
	}

	return FX_S_OK;
}

result_t JvmScriptRuntime::ShutdownFxProfiler()
{
	JNIEnv* jniEnv = JvmComponentHost::GetJNIEnv();
	if (!jniEnv || !m_stopProfilingMethod)
	{
		return FX_E_INVALIDARG;
	}

	jniEnv->CallStaticVoidMethod(m_scriptInterfaceClass, m_stopProfilingMethod);

	if (jniEnv->ExceptionCheck())
	{
		jniEnv->ExceptionDescribe();
		jniEnv->ExceptionClear();
		return FX_E_INVALIDARG;
	}

	return FX_S_OK;
}

jarray JvmScriptRuntime::CanonicalizeRef(int referenceId) const
{
	// TODO: Implement reference canonicalization
	return nullptr;
}

jarray JvmScriptRuntime::InvokeFunctionReference(jstring referenceId, jarray argsSerialized) const
{
	// TODO: Implement function reference invocation
	return nullptr;
}

bool JvmScriptRuntime::ReadClass(jstring name, jbyteArray* classBytes) const
{
	// TODO: Implement class reading for UGC scripts
	return false;
}
}

// Register runtime
static InitFunction initFunction([] ()
{
	fx::ResourceManager::OnInitializeInstance.Connect([](fx::ResourceManager* instance)
	{
		instance->GetComponent<fx::ResourceScriptingComponent>()->SetScriptRuntimeFactory([](fx::Resource* resource)
		{
			return new fx::jvm::JvmScriptRuntime();
		});
	});
});
