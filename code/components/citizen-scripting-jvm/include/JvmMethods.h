// explicit include guard as we may include this with different names
#ifndef JVM_METHODS_H
#define JVM_METHODS_H

#include "StdInc.h"
#include <string>
#include <jni.h>

namespace fx::jvm
{
	// Helper to get JNIEnv for the current thread
	JNIEnv* GetThreadEnv();

	// Method wrapper for Java methods
	class Method
	{
	private:
		jmethodID method;
		bool isStatic;

	public:
		Method() : method(nullptr), isStatic(false) {}
		Method(std::nullptr_t) : method(nullptr), isStatic(false) {}
		Method(jmethodID method, bool isStatic = false) : method(method), isStatic(isStatic) {}

		~Method() = default;

		// Searches the class for the given method
		static Method Find(JNIEnv* env, jclass cls, const char* methodName, const char* signature, bool isStatic = false)
		{
			jmethodID methodId = isStatic
				? env->GetStaticMethodID(cls, methodName, signature)
				: env->GetMethodID(cls, methodName, signature);

			if (env->ExceptionCheck())
			{
				env->ExceptionClear();
				return Method(nullptr);
			}

			return Method(methodId, isStatic);
		}

		// Call with jobject arguments
		inline jobject operator()(JNIEnv* env, jobject _this, ...) const
		{
			if (!method) return nullptr;

			va_list args;
			va_start(args, _this);
			jobject result = env->CallObjectMethodV(_this, method, args);
			va_end(args);

			return result;
		}

		// Call static method
		inline jobject CallStatic(JNIEnv* env, jclass cls, ...) const
		{
			if (!method) return nullptr;

			va_list args;
			va_start(args, cls);
			jobject result = env->CallStaticObjectMethodV(cls, method, args);
			va_end(args);

			return result;
		}

		// Call void method
		inline void CallVoid(JNIEnv* env, jobject _this, ...) const
		{
			if (!method) return;

			va_list args;
			va_start(args, _this);
			env->CallVoidMethodV(_this, method, args);
			va_end(args);
		}

		// Call static void method
		inline void CallStaticVoid(JNIEnv* env, jclass cls, ...) const
		{
			if (!method) return;

			va_list args;
			va_start(args, cls);
			env->CallStaticVoidMethodV(cls, method, args);
			va_end(args);
		}

		jmethodID GetMethodID() const
		{
			return method;
		}

		bool IsStatic() const
		{
			return isStatic;
		}

		Method& operator=(std::nullptr_t)
		{
			method = nullptr;
			isStatic = false;
			return *this;
		}

		inline operator bool() const
		{
			return method != nullptr;
		}
	};

	// For JVM, we don't have "thunks" like Mono, but we can create a similar abstraction
	// that caches method IDs for fast calls
	template<typename Func>
	class Thunk;

	template<typename Ret, typename... Args>
	class Thunk<Ret(Args...)>
	{
	public:
		using return_type = Ret;

	private:
		jmethodID methodId;
		jclass cls;
		bool isStatic;

	public:
		Thunk() : methodId(nullptr), cls(nullptr), isStatic(false) {}
		Thunk(std::nullptr_t) : methodId(nullptr), cls(nullptr), isStatic(false) {}
		Thunk(jmethodID methodId, jclass cls, bool isStatic = false)
			: methodId(methodId), cls(cls), isStatic(isStatic) {}

		Thunk(const Method& method, jclass cls)
			: methodId(method.GetMethodID()), cls(cls), isStatic(method.IsStatic()) {}

		// Searches the class for the given method
		static Thunk Find(JNIEnv* env, jclass cls, const char* methodName, const char* signature, bool isStatic = false)
		{
			Method method = Method::Find(env, cls, methodName, signature, isStatic);
			return method ? Thunk(method, cls) : Thunk();
		}

		Thunk& operator=(const Method& copy)
		{
			methodId = copy.GetMethodID();
			isStatic = copy.IsStatic();
			return *this;
		}

		Thunk& operator=(std::nullptr_t)
		{
			methodId = nullptr;
			cls = nullptr;
			isStatic = false;
			return *this;
		}

		// Note: For JVM, actual invocation would need JNIEnv and proper argument marshaling
		// This is a simplified interface that matches Mono's pattern
		inline operator bool() const
		{
			return methodId != nullptr;
		}

		jmethodID GetMethodID() const { return methodId; }
		jclass GetClass() const { return cls; }
		bool IsStatic() const { return isStatic; }
	};
}

#endif
