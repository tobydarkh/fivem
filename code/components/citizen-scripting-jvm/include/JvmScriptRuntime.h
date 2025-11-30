/*
 * This file is part of the CitizenFX project - http://citizen.re/
 *
 * See LICENSE and MENTIONS in the root of the source tree for information
 * regarding licensing.
 */

#pragma once

#include "StdInc.h"

#include <deque>
#include <jni.h>

#include <fxScripting.h>
#include <Resource.h>
#include <ManifestVersion.h>

#include <om/OMComponent.h>

#include "JvmMethods.h"
#include "ScriptSharedData.h"

namespace fx::jvm
{
class JvmScriptRuntime : public fx::OMClass<JvmScriptRuntime, IScriptRuntime, IScriptFileHandlingRuntime, IScriptTickRuntime,
	IScriptEventRuntime, IScriptRefRuntime, IScriptMemInfoRuntime, IScriptDebugRuntime, IScriptProfiler>
{
private:
	int m_instanceId;
	std::string m_name;
	std::string m_resourceName;
	jobject m_classLoader;  // Custom ClassLoader for this resource
	jclass m_scriptInterfaceClass;  // Local reference to ScriptInterface class
	int32_t m_classLoaderId;

	// Direct host access
	IScriptHost* m_scriptHost;
	IScriptHostWithResourceData* m_resourceHost;
	IScriptHostWithManifest* m_manifestHost;

	fx::OMPtr<IScriptRuntimeHandler> m_handler;
	fx::Resource* m_parentObject;
	IDebugEventListener* m_debugListener;
	std::unordered_map<std::string, int> m_scriptIds;

	ScriptSharedData m_sharedData;

	// method targets
	jmethodID m_loadClass;

	// method IDs for performance-critical calls
	jmethodID m_tickMethod;
	jmethodID m_triggerEventMethod;
	jmethodID m_callRefMethod;
	jmethodID m_duplicateRefMethod;
	jmethodID m_removeRefMethod;
	jmethodID m_getMemoryUsageMethod;
	jmethodID m_startProfilingMethod;
	jmethodID m_stopProfilingMethod;

public:
	JvmScriptRuntime();

	virtual ~JvmScriptRuntime() override;

	jarray CanonicalizeRef(int referenceId) const;

	jarray InvokeFunctionReference(jstring referenceId, jarray argsSerialized) const;

	bool ReadClass(jstring name, jbyteArray* classBytes) const;

private:
	void InitializeMethods(JNIEnv* env);

public:
	NS_DECL_ISCRIPTRUNTIME;

	NS_DECL_ISCRIPTFILEHANDLINGRUNTIME;

	NS_DECL_ISCRIPTTICKRUNTIME;

	NS_DECL_ISCRIPTEVENTRUNTIME;

	NS_DECL_ISCRIPTREFRUNTIME;

	NS_DECL_ISCRIPTMEMINFORUNTIME;

	NS_DECL_ISCRIPTDEBUGRUNTIME;

	NS_DECL_ISCRIPTPROFILER;

	// Helper to get instance ID (used in JNI callbacks)
	int GetInstanceId() const { return m_instanceId; }
	jobject GetClassLoader() const { return m_classLoader; }
};

inline JvmScriptRuntime::JvmScriptRuntime()
	: m_classLoader(nullptr)
	, m_scriptInterfaceClass(nullptr)
	, m_classLoaderId(0)
	, m_scriptHost(nullptr)
	, m_resourceHost(nullptr)
	, m_manifestHost(nullptr)
	, m_parentObject(nullptr)
	, m_debugListener(nullptr)
	, m_loadClass(nullptr)
	, m_tickMethod(nullptr)
	, m_triggerEventMethod(nullptr)
	, m_callRefMethod(nullptr)
	, m_duplicateRefMethod(nullptr)
	, m_removeRefMethod(nullptr)
	, m_getMemoryUsageMethod(nullptr)
	, m_startProfilingMethod(nullptr)
	, m_stopProfilingMethod(nullptr)
{
	m_instanceId = rand();
	m_name = "ScriptDomain_" + std::to_string(m_instanceId);
}
}
