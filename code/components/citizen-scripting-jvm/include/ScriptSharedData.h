#pragma once

#include <atomic>

namespace fx::jvm
{
struct ScriptSharedData
{
public:
	std::atomic<uint64_t> m_scheduledTime = ~uint64_t(0);
};
}
