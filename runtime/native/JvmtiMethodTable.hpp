#pragma once
#include <windows.h>
#include <jni.h>
#include <jvmti.h>
#include <atomic>
#include <cstddef>
#include <cstring>
#include <cstdio>
#include <string>
#include <utility>
#include <mutex>
#include <vector>



namespace JvmtiMethodTable {
static_assert(sizeof(void*) == 8);
static_assert(sizeof(jvmtiInterface_1) == 0x4e0);
static_assert(offsetof(jvmtiInterface_1, RedefineClasses) == 0x2b0);
static_assert(offsetof(jvmtiInterface_1, GetClassMethods) == 0x198);
static_assert(offsetof(jvmtiInterface_1, RetransformClasses) == 0x4b8);
static_assert(offsetof(jvmtiInterface_1, GetLoadedClasses) == 0x268);
static_assert(offsetof(jvmtiInterface_1, IterateOverInstancesOfClass) == 0x378);

static constexpr jlong protectedTag = 0xdead1337LL;
static jvmtiInterface_1 original{}, wrapped{};
static const jvmtiInterface_1* sharedTable = nullptr;
static jvmtiEnv* primary = nullptr;
static JavaVM* vm = nullptr;
static HANDLE stopEvent = nullptr, watcher = nullptr;
static std::mutex lifecycle;
static std::atomic<bool> installed{false};
static std::atomic<unsigned long> blockedCalls{0};
static std::vector<std::string> hiddenMethodPrefixes;




static bool ConfigureMethodHiding(std::vector<std::string> prefixes) {
    std::lock_guard<std::mutex> lock(lifecycle);
    if (installed.load() || prefixes.empty()) return false;
    for (const auto& prefix : prefixes)
        if (prefix.empty()) return false;
    hiddenMethodPrefixes = std::move(prefixes);
    return true;
}

static void ReportError(JNIEnv* env, const char* operation, jvmtiError error) {
    if (error == JVMTI_ERROR_NONE) return;
    char message[256];
    std::snprintf(message, sizeof(message), "%s failed: JVMTI error %d", operation, static_cast<int>(error));
    LOG("ERROR", "JVMTI.Table", "%s", message);
    if (env && !env->ExceptionCheck()) {
        jclass failure = env->FindClass("java/lang/IllegalStateException");
        if (failure) { env->ThrowNew(failure, message); env->DeleteLocalRef(failure); }
    }
}

static void SetTable(jvmtiEnv* env, const jvmtiInterface_1* table) {
    InterlockedExchangePointer(reinterpret_cast<PVOID volatile*>(
                                   const_cast<jvmtiInterface_1**>(&env->functions)),
                               const_cast<jvmtiInterface_1*>(table));
}

static const jvmtiInterface_1* GetTable(jvmtiEnv* env) {
    return static_cast<const jvmtiInterface_1*>(InterlockedCompareExchangePointer(
        reinterpret_cast<PVOID volatile*>(const_cast<jvmtiInterface_1**>(&env->functions)), nullptr, nullptr));
}

static bool IsProtected(jclass cls) {
    if (!cls || !primary || !original.GetTag) return false;
    jlong tag = 0;
    return original.GetTag(primary, cls, &tag) == JVMTI_ERROR_NONE && tag == protectedTag;
}



static jvmtiError Denied(const char* operation, jclass cls) {
    char* signature = nullptr;
    if (cls && primary) original.GetClassSignature(primary, cls, &signature, nullptr);
    LOG("ERROR", "JVMTI.Table", "%s denied: protected class=%s tag=0x%llx err=%d",
        operation, signature ? signature : "<unavailable>",
        static_cast<unsigned long long>(protectedTag), JVMTI_ERROR_ACCESS_DENIED);
    if (signature) original.Deallocate(primary, reinterpret_cast<unsigned char*>(signature));
    return JVMTI_ERROR_ACCESS_DENIED;
}

static jvmtiError JNICALL Redefine(jvmtiEnv* env, jint count, const jvmtiClassDefinition* defs) {
    if (!original.RedefineClasses) return JVMTI_ERROR_NONE;
    if (defs) for (jint i = 0; i < count; ++i)
        if (IsProtected(defs[i].klass)) return Denied("RedefineClasses", defs[i].klass);
    auto err = original.RedefineClasses(env, count, defs);
    LOG(err ? "ERROR" : "INFO", "JVMTI.Table", "RedefineClasses count=%d err=%d env=%p primary=%p", count, err, (void*)env, (void*)primary);
    return err;
}

static jvmtiError JNICALL Retransform(jvmtiEnv* env, jint count, const jclass* classes) {
    if (!original.RetransformClasses) return JVMTI_ERROR_NONE;
    if (classes) for (jint i = 0; i < count; ++i)
        if (IsProtected(classes[i])) return Denied("RetransformClasses", classes[i]);
    auto err = original.RetransformClasses(env, count, classes);
    LOG(err ? "ERROR" : "INFO", "JVMTI.Table", "RetransformClasses count=%d err=%d env=%p primary=%p", count, err, (void*)env, (void*)primary);
    return err;
}





static jvmtiError JNICALL ClassMethods(jvmtiEnv* env, jclass cls,
                                      jint* count, jmethodID** methods) {
    if (!original.GetClassMethods) return JVMTI_ERROR_NOT_AVAILABLE;
    if (!cls || !count || !methods) return original.GetClassMethods(env, cls, count, methods);
    char* signature = nullptr;
    if (original.GetClassSignature && original.Deallocate) {
        original.GetClassSignature(env, cls, &signature, nullptr);
        if (signature) {
            bool hidden = false;
            for (const auto& prefix : hiddenMethodPrefixes)
                if (std::strstr(signature, prefix.c_str())) { hidden = true; break; }
            original.Deallocate(env, reinterpret_cast<unsigned char*>(signature));
            if (hidden) {
                *count = 0;
                *methods = nullptr;
                return JVMTI_ERROR_NONE;
            }
        }
    }
    return original.GetClassMethods(env, cls, count, methods);
}

static jvmtiError JNICALL Loaded(jvmtiEnv* env, jint* count, jclass** classes) {
    auto err = original.GetLoadedClasses(env, count, classes);
    if (err || !count || !classes || *count <= 0) return err;
    try {
        std::vector<jclass> visible;
        std::vector<jclass> hidden;
        visible.reserve(*count);
        hidden.reserve(*count);
        for (jint i = 0; i < *count; ++i)
            (IsProtected((*classes)[i]) ? hidden : visible).push_back((*classes)[i]);
        if (visible.size() == static_cast<size_t>(*count)) return JVMTI_ERROR_NONE;
        unsigned char* memory = nullptr;
        err = original.Allocate(env, static_cast<jlong>(visible.size() * sizeof(jclass)), &memory);
        if (err) {

            LOG("WARN", "JVMTI.Table", "GetLoadedClasses filter allocation err=%d; original result retained", err);
            return JVMTI_ERROR_NONE;
        }
        if (!visible.empty()) std::memcpy(memory, visible.data(), visible.size() * sizeof(jclass));
        JNIEnv* jni = nullptr;
        if (vm && vm->GetEnv(reinterpret_cast<void**>(&jni), JNI_VERSION_1_8) == JNI_OK) {
            for (jclass cls : hidden) jni->DeleteLocalRef(cls);
        }
        const jint removed = *count - static_cast<jint>(visible.size());
        original.Deallocate(env, reinterpret_cast<unsigned char*>(*classes));
        *classes = reinterpret_cast<jclass*>(memory);
        *count = static_cast<jint>(visible.size());
        LOG("INFO", "JVMTI.Table", "GetLoadedClasses visible=%d filtered=%d", *count, removed);
    } catch (const std::bad_alloc&) {
        LOG("WARN", "JVMTI.Table", "GetLoadedClasses filter out of memory; original result retained");
    }
    return JVMTI_ERROR_NONE;
}

static jvmtiError JNICALL Instances(jvmtiEnv*, jclass, jvmtiHeapObjectFilter,
                                     jvmtiHeapObjectCallback, const void*) {
    LOG("WARN", "JVMTI.Table", "IterateOverInstancesOfClass blocked err=%d", JVMTI_ERROR_NATIVE_METHOD);
    return JVMTI_ERROR_NATIVE_METHOD;
}



static jvmtiError JNICALL Blocked(jvmtiEnv*, ...) {
    const auto n = ++blockedCalls;
    if (n == 1 || (n & (n - 1)) == 0)
        LOG("WARN", "JVMTI.Table", "Original-table call blocked err=%d total=%lu", JVMTI_ERROR_WRONG_PHASE, n);
    return JVMTI_ERROR_WRONG_PHASE;
}

static bool WriteShared(bool block) {
    DWORD previous = 0;
    auto destination = const_cast<jvmtiInterface_1*>(sharedTable);
    if (!VirtualProtect(destination, sizeof(original), PAGE_READWRITE, &previous)) {
        LOG("ERROR", "JVMTI.Table", "VirtualProtect table failed win32=%lu", GetLastError());
        return false;
    }
    for (size_t offset = 0; offset < sizeof(original); offset += sizeof(void*)) {
        void* address = reinterpret_cast<void*>(&Blocked);
        if (!block) std::memcpy(&address, reinterpret_cast<const char*>(&original) + offset, sizeof(address));
        InterlockedExchangePointer(reinterpret_cast<PVOID volatile*>(
            reinterpret_cast<char*>(destination) + offset), address);
    }
    DWORD ignored = 0;
    if (!VirtualProtect(destination, sizeof(original), previous, &ignored)) {
        LOG("ERROR", "JVMTI.Table", "Restoring table page protection failed win32=%lu", GetLastError());
        return false;
    }
    return true;
}

static DWORD WINAPI Watch(LPVOID) {
    while (WaitForSingleObject(stopEvent, 100) == WAIT_TIMEOUT) {
        if (primary && GetTable(primary) != &wrapped) {
            SetTable(primary, &wrapped);
            LOG("WARN", "JVMTI.Table", "Primary function table restored by 100ms watcher");
        }
    }
    return 0;
}



static bool Restore() {
    std::lock_guard<std::mutex> lock(lifecycle);
    if (!installed.load()) return true;
    SetEvent(stopEvent);
    if (watcher) { WaitForSingleObject(watcher, INFINITE); CloseHandle(watcher); watcher = nullptr; }
    if (!WriteShared(false)) return false;
    SetTable(primary, sharedTable);
    installed.store(false);
    CloseHandle(stopEvent); stopEvent = nullptr;
    LOG("INFO", "JVMTI.Table", "Original table restored; blocked calls=%lu", blockedCalls.load());
    return true;
}

static jvmtiError Install(JavaVM* java, jvmtiEnv* env) {
    std::lock_guard<std::mutex> lock(lifecycle);
    if (installed.load()) return env == primary ? JVMTI_ERROR_NONE : JVMTI_ERROR_INTERNAL;
    if (!java || !env || !env->functions) return JVMTI_ERROR_NULL_POINTER;
    original = *env->functions;
    if (hiddenMethodPrefixes.empty() || !original.GetClassMethods || !original.GetClassSignature ||
        !original.RedefineClasses || !original.RetransformClasses || !original.GetTag ||
        !original.SetTag || !original.GetLoadedClasses || !original.Allocate || !original.Deallocate)
        return JVMTI_ERROR_NOT_AVAILABLE;
    jvmtiCapabilities caps{}; caps.can_tag_objects = 1;
    auto err = original.AddCapabilities(env, &caps);
    if (err) return err;
    wrapped = original;
    wrapped.RedefineClasses = &Redefine;
    wrapped.RetransformClasses = &Retransform;
    wrapped.GetClassMethods = &ClassMethods;
    wrapped.GetLoadedClasses = &Loaded;
    wrapped.IterateOverInstancesOfClass = &Instances;
    vm = java; primary = env; sharedTable = env->functions;
    stopEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    if (!stopEvent) return JVMTI_ERROR_OUT_OF_MEMORY;
    SetTable(primary, &wrapped);
    if (!WriteShared(true)) {
        WriteShared(false); SetTable(primary, sharedTable);
        CloseHandle(stopEvent); stopEvent = nullptr;
        return JVMTI_ERROR_INTERNAL;
    }
    watcher = CreateThread(nullptr, 0, &Watch, nullptr, 0, nullptr);
    if (!watcher) {
        WriteShared(false); SetTable(primary, sharedTable);
        CloseHandle(stopEvent); stopEvent = nullptr;
        return JVMTI_ERROR_OUT_OF_MEMORY;
    }
    installed.store(true);
    LOG("INFO", "JVMTI.Table", "Installed table bytes=0x%zx slots=5 methodPrefixes=%zu protectedTag=0x%llx action=log-and-error watcher=100ms",
        sizeof(original), hiddenMethodPrefixes.size(), static_cast<unsigned long long>(protectedTag));
    return JVMTI_ERROR_NONE;
}

static jvmtiError Protect(jclass cls) {
    if (!installed.load() || !primary) return JVMTI_ERROR_WRONG_PHASE;
    const auto err = original.SetTag(primary, cls, protectedTag);
    if (err) LOG("ERROR", "JVMTI.Table", "SetTag failed err=%d", err);
    return err;
}



class Environment {
    jvmtiEnv* value = nullptr;
public:
    explicit Environment(JavaVM* java) {
        if (java && java->GetEnv(reinterpret_cast<void**>(&value), JVMTI_VERSION_1_2) == JNI_OK && value) {
            if (installed.load()) SetTable(value, &wrapped);
        } else value = nullptr;
    }
    ~Environment() {
        if (value) {
            const auto err = installed.load() ? original.DisposeEnvironment(value) : value->DisposeEnvironment();
            if (err) LOG("ERROR", "JVMTI.Table", "DisposeEnvironment failed err=%d", err);
        }
    }
    Environment(const Environment&) = delete;
    Environment& operator=(const Environment&) = delete;
    jvmtiEnv* get() const { return value; }
};
}
