#include "codegen.h"
#include "jar.h"
#include "jvmti_table_source.h"
#include <sstream>
#include <iomanip>
#include <chrono>
#include <algorithm>
#include <cstring>
#include <set>

CodeGen::CodeGen() : rng(std::random_device{}()) {
    auto now = std::chrono::high_resolution_clock::now().time_since_epoch().count();
    rng.seed(static_cast<uint32_t>(now ^ (now >> 32)));
}

std::string CodeGen::rndName(int minLen, int maxLen) {
    static const char* ch = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    std::string r;
    r += ch[rng() % 52];
    int len = minLen + static_cast<int>(rng() % (maxLen - minLen + 1));
    for (int i = 0; i < len; i++) r += ch[rng() % 52];
    return r;
}




void CodeGen::emitTraceCounters(std::stringstream& cpp) {
    cpp << "#include <atomic>\n";
    cpp << "static std::atomic<int> g_tc_classesObserved{0};\n";
    cpp << "static std::atomic<int> g_tc_originalBytesCached{0};\n";
    cpp << "static std::atomic<int> g_tc_protectedClasses{0};\n";
    cpp << "static std::atomic<int> g_tc_mixinTargetsFound{0};\n";
    cpp << "static std::atomic<int> g_tc_loadedTargetsFound{0};\n";
    cpp << "static std::atomic<int> g_tc_targetsTransformed{0};\n";
    cpp << "static std::atomic<int> g_tc_redefineSucceeded{0};\n";
    cpp << "static std::atomic<int> g_tc_redefineSkippedNoBytes{0};\n";
    cpp << "static std::atomic<int> g_tc_cacheCleared{0};\n";
    cpp << "static void PrintTraceCounters() {\n";
    cpp << "    LOG(\"INFO\",\"Trace\",\"classesObserved=%d originalBytesCached=%d protectedClasses=%d\",\n";
    cpp << "        g_tc_classesObserved.load(),g_tc_originalBytesCached.load(),g_tc_protectedClasses.load());\n";
    cpp << "    LOG(\"INFO\",\"Trace\",\"mixinTargetsFound=%d loadedTargetsFound=%d targetsTransformed=%d\",\n";
    cpp << "        g_tc_mixinTargetsFound.load(),g_tc_loadedTargetsFound.load(),g_tc_targetsTransformed.load());\n";
    cpp << "    LOG(\"INFO\",\"Trace\",\"redefineSucceeded=%d redefineSkippedNoBytes=%d cacheCleared=%d\",\n";
    cpp << "        g_tc_redefineSucceeded.load(),g_tc_redefineSkippedNoBytes.load(),g_tc_cacheCleared.load());\n";
    cpp << "}\n\n";
}











void CodeGen::emitRuntimeClassArrays(std::stringstream& cpp,
                                      const CodeGenConfig& config,
                                      std::map<std::string, std::string>& rtVar) {
    if (config.runtimeClasses.empty()) {
        return;
    }



    for (const auto& rc : config.runtimeClasses) {

        std::string var = rndName(8, 12) + "_rt";
        rtVar[rc.internalName] = var;

        cpp << "static const uint8_t " << var << "[] = {";
        for (size_t i = 0; i < rc.bytecode.size(); i++) {
            if (i % 20 == 0) cpp << "\n    ";
            cpp << (unsigned int)(uint8_t)rc.bytecode[i];
            if (i < rc.bytecode.size() - 1) cpp << ",";
        }
        cpp << "\n};\n";
        cpp << "static const jsize " << var << "_sz = " << rc.bytecode.size() << ";\n\n";
    }


    cpp << "static void LoadRuntimeClasses() {\n";
    for (const auto& rc : config.runtimeClasses) {
        const auto& var = rtVar.at(rc.internalName);

        std::string jvmName = rc.internalName;
        cpp << "    if (!LoadClass(\"" << jvmName << "\", (jbyte*)" << var
            << ", " << var << "_sz))\n";
        cpp << "        LOG(\"WARN\",\"G8\",\"Failed to embed: " << rc.internalName << "\");\n";
        cpp << "    else\n";
        cpp << "        LOG(\"INFO\",\"G8\",\"Embedded: " << rc.internalName << " ("
            << rc.bytecode.size() << " bytes)\");\n";
    }
    cpp << "    LOG(\"INFO\",\"G8\",\"" << config.runtimeClasses.size()
        << " runtime classes embedded\");\n";
    cpp << "}\n\n";
}


void CodeGen::emitClassByteCache(std::stringstream& cpp) {
    cpp << "#include <mutex>\n";
    cpp << "#include <unordered_map>\n";
    cpp << "static std::unordered_map<jclass, std::vector<uint8_t>> g_originalClassBytes;\n";
    cpp << "static std::mutex g_classBytesMutex;\n";
    cpp << "static std::set<jclass> g_ownRedefineInProgress;\n";
    cpp << "static std::mutex g_ownRedefinesMutex;\n\n";



    cpp << R"CAPTURE(
static std::mutex g_captureMutex;
static jvmtiEventCallbacks g_primaryCallbacks{};
static bool g_primaryClassHookEnabled = false;
static std::mutex g_captureStateMutex;
static jclass g_captureTarget = nullptr;
static std::string g_captureName;
static std::vector<uint8_t> g_captureBytes;
static bool SameCapturedBytes(JNIEnv* env, const std::vector<uint8_t>& original, jbyteArray array) {
    if (!array || (size_t)env->GetArrayLength(array) != original.size()) return false;
    jbyte* data = env->GetByteArrayElements(array, nullptr);
    if (!data) return false;
    bool equal = memcmp(original.data(), data, original.size()) == 0;
    env->ReleaseByteArrayElements(array, data, JNI_ABORT);
    return equal;
}
static void JNICALL CaptureLoadedClass(jvmtiEnv*, JNIEnv* env, jclass cls,
        jobject, const char* name, jobject, jint length, const unsigned char* bytes,
        jint*, unsigned char**) {
    std::lock_guard<std::mutex> stateLock(g_captureStateMutex);
    if (cls && name && g_captureTarget && g_captureName == name
            && env->IsSameObject(cls, g_captureTarget)
            && bytes && length > 0)
        g_captureBytes.assign(bytes, bytes + length);
}
static std::vector<uint8_t> GetOriginalBytes(JNIEnv* env, jclass cls) {
    if (!env || !cls || !g_jvm) return {};
    std::lock_guard<std::mutex> captureLock(g_captureMutex);
    jvmtiEnv* capture = g_jvmti;
    if (!capture) {
        JvmtiMethodTable::ReportError(env, "Capture(primary environment)", JVMTI_ERROR_WRONG_PHASE);
        return {};
    }
    jvmtiCapabilities caps{};
    caps.can_retransform_classes = 1;
    caps.can_generate_all_class_hook_events = 1;
    jvmtiError err = capture->AddCapabilities(&caps);
    jvmtiEventCallbacks callbacks = g_primaryCallbacks;
    callbacks.ClassFileLoadHook = CaptureLoadedClass;
    char* signature = nullptr;
    jvmtiError signatureError = capture->GetClassSignature(cls, &signature, nullptr);
    if (signatureError != JVMTI_ERROR_NONE || !signature) {
        JvmtiMethodTable::ReportError(env, "Capture class signature",
            signatureError != JVMTI_ERROR_NONE ? signatureError : JVMTI_ERROR_INTERNAL);
        return {};
    }
    std::string targetName(signature);
    capture->Deallocate(reinterpret_cast<unsigned char*>(signature));
    if (targetName.size() >= 2 && targetName.front() == 'L' && targetName.back() == ';')
        targetName = targetName.substr(1, targetName.size() - 2);
    jclass targetRef = (jclass)env->NewGlobalRef(cls);
    if (!targetRef) return {};
    {
        std::lock_guard<std::mutex> stateLock(g_captureStateMutex);
        g_captureBytes.clear();
        g_captureName = targetName;
        g_captureTarget = targetRef;
    }
    LOG("INFO","G4","Capture begin target=%s primary=%p", targetName.c_str(), (void*)capture);
    bool callbacksChanged = false;
    if (err == JVMTI_ERROR_NONE) {
        err = capture->SetEventCallbacks(&callbacks, sizeof(callbacks));
        callbacksChanged = err == JVMTI_ERROR_NONE;
    }
    if (err == JVMTI_ERROR_NONE)
        err = capture->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
    if (err == JVMTI_ERROR_NONE)
        err = capture->RetransformClasses(1, &cls);
    if (callbacksChanged) {
        jvmtiError cleanup = capture->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
        jvmtiError restore = capture->SetEventCallbacks(&g_primaryCallbacks, sizeof(g_primaryCallbacks));
        if (!cleanup) cleanup = restore;
        if (g_primaryClassHookEnabled) {
            restore = capture->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
            if (!cleanup) cleanup = restore;
        }
        if (cleanup) {
            LOG("ERROR","G4","Primary callback restoration failed err=%d", (int)cleanup);
            if (!err) err = cleanup;
        }
    }
    std::vector<uint8_t> captured;
    {
        std::lock_guard<std::mutex> stateLock(g_captureStateMutex);
        g_captureTarget = nullptr;
        g_captureName.clear();
        captured.swap(g_captureBytes);
    }
    env->DeleteGlobalRef(targetRef);
    if (err != JVMTI_ERROR_NONE || captured.empty()) {
        LOG("ERROR","G4","Loaded bytecode capture failed: err=%d bytes=%zu", (int)err, captured.size());
        if (!env->ExceptionCheck()) {
            jclass failure = env->FindClass("java/lang/IllegalStateException");
            if (failure) env->ThrowNew(failure, "Cannot capture loaded class bytes through JVMTI");
        }
        return {};
    }
    LOG("INFO","G4","Captured loaded bytecode through primary RetransformClasses: env=%p table=%p bytes=%zu",
        (void*)capture, (void*)capture->functions, captured.size());
    ++g_tc_originalBytesCached;
    return captured;
}
)CAPTURE";
}





void CodeGen::emitNativeBridge(std::stringstream& cpp) {
    cpp << "static void RegisterNativeBridge() {\n";
    cpp << "    jclass bridgeCls = FindClassByLoader(\"mod.runtime.NativeBridge\");\n";
    cpp << "    if (!bridgeCls) { LOG(\"WARN\",\"Bridge\",\"NativeBridge class not found вЂ” fallback mode\"); return; }\n\n";


    cpp << "    auto fn_getMixinResource0 = [](JNIEnv* e, jobject, jstring jn) -> jbyteArray {\n";
    cpp << "        if (!jn) return nullptr;\n";
    cpp << "        const char* n = e->GetStringUTFChars(jn, nullptr);\n";
    cpp << "        std::string key(n); e->ReleaseStringUTFChars(jn, n);\n";
    cpp << "        if (!key.empty() && key[0] == '/') key = key.substr(1);\n";
    cpp << "        auto it = g_resources.find(key);\n";
    cpp << "        if (it == g_resources.end()) return nullptr;\n";
    cpp << "        jbyteArray arr = e->NewByteArray((jsize)it->second.size());\n";
    cpp << "        if (arr) e->SetByteArrayRegion(arr, 0, (jsize)it->second.size(), (jbyte*)it->second.data());\n";
    cpp << "        return arr;\n";
    cpp << "    };\n\n";


    cpp << "    auto fn_listMixinResources0 = [](JNIEnv* e, jobject) -> jobjectArray {\n";
    cpp << "        jclass strCls = e->FindClass(\"java/lang/String\");\n";
    cpp << "        jobjectArray arr = e->NewObjectArray((jsize)g_resources.size(), strCls, nullptr);\n";
    cpp << "        if (!arr) return nullptr;\n";
    cpp << "        jsize idx = 0;\n";
    cpp << "        for (const auto& kv : g_resources) {\n";
    cpp << "            jstring s = e->NewStringUTF(kv.first.c_str());\n";
    cpp << "            e->SetObjectArrayElement(arr, idx++, s);\n";
    cpp << "            e->DeleteLocalRef(s);\n";
    cpp << "        }\n";
    cpp << "        return arr;\n";
    cpp << "    };\n\n";


    cpp << "    auto fn_getLoadedClassBytes0 = [](JNIEnv* e, jobject, jclass cls) -> jbyteArray {\n";
    cpp << "        if (!cls) return nullptr;\n";
    cpp << "        auto v = GetOriginalBytes(e, cls);\n";
    cpp << "        if (v.empty()) return nullptr;\n";
    cpp << "        jbyteArray arr = e->NewByteArray((jsize)v.size());\n";
    cpp << "        if (arr) e->SetByteArrayRegion(arr, 0, (jsize)v.size(), (jbyte*)v.data());\n";
    cpp << "        return arr;\n";
    cpp << "    };\n\n";


    cpp << "    auto fn_redefineClass0 = [](JNIEnv* e, jobject, jclass cls, jbyteArray bytes) {\n";
    cpp << "        if (!cls || !bytes) return;\n";
    cpp << "        jvmtiEnv* jt = g_jvmti;\n";
    cpp << "        if (!jt) { JvmtiMethodTable::ReportError(e, \"Redefine(primary environment)\", JVMTI_ERROR_WRONG_PHASE); return; }\n";
    cpp << "        jvmtiCapabilities caps = {};\n";
    cpp << "        caps.can_redefine_classes = 1; caps.can_redefine_any_class = 1;\n";
    cpp << "        jvmtiError capError = jt->AddCapabilities(&caps);\n";
    cpp << "        if (capError) { JvmtiMethodTable::ReportError(e, \"AddCapabilities(redefine)\", capError); return; }\n";
    cpp << "        jint bl = e->GetArrayLength(bytes);\n";
    cpp << "        jbyte* buf = e->GetByteArrayElements(bytes, nullptr);\n";
    cpp << "        if (!buf) return;\n";
    cpp << "        { std::lock_guard<std::mutex> lk(g_ownRedefinesMutex);\n";
    cpp << "          g_ownRedefineInProgress.insert(cls); }\n";
    cpp << "        jvmtiClassDefinition def = {cls, bl, (unsigned char*)buf};\n";
    cpp << "        jvmtiError err = jt->RedefineClasses(1, &def);\n";
    cpp << "        if (err == JVMTI_ERROR_NONE) ++g_tc_redefineSucceeded;\n";
    cpp << "        { std::lock_guard<std::mutex> lk(g_ownRedefinesMutex);\n";
    cpp << "          g_ownRedefineInProgress.erase(cls); }\n";
    cpp << "        e->ReleaseByteArrayElements(bytes, buf, JNI_ABORT);\n";
    cpp << "        LOG(\"INFO\",\"Bridge\",\"redefineClass0: err=%d\", (int)err);\n";
    cpp << "        JvmtiMethodTable::ReportError(e, \"RedefineClasses\", err);\n";
    cpp << "    };\n\n";


    cpp << "    auto fn_retransformClasses0 = [](JNIEnv* e, jobject, jobjectArray cls_arr) {\n";
    cpp << "        if (!cls_arr) return;\n";
    cpp << "        jvmtiEnv* jt = g_jvmti;\n";
    cpp << "        if (!jt) { JvmtiMethodTable::ReportError(e, \"Retransform(primary environment)\", JVMTI_ERROR_WRONG_PHASE); return; }\n";
    cpp << "        jvmtiCapabilities caps = {};\n";
    cpp << "        caps.can_retransform_classes = 1; caps.can_retransform_any_class = 1;\n";
    cpp << "        jvmtiError capError = jt->AddCapabilities(&caps);\n";
    cpp << "        if (capError) { JvmtiMethodTable::ReportError(e, \"AddCapabilities(retransform)\", capError); return; }\n";
    cpp << "        jint cnt = e->GetArrayLength(cls_arr);\n";
    cpp << "        std::vector<jclass> v;\n";
    cpp << "        for (jint i = 0; i < cnt; i++) {\n";
    cpp << "            jclass c = (jclass)e->GetObjectArrayElement(cls_arr, i);\n";
    cpp << "            if (c) v.push_back(c);\n";
    cpp << "        }\n";
    cpp << "        if (!v.empty()) {\n";
    cpp << "            JvmtiMethodTable::ReportError(e, \"RetransformClasses\", jt->RetransformClasses((jint)v.size(), v.data()));\n";
    cpp << "        }\n";
    cpp << "        for (jclass c : v) e->DeleteLocalRef(c);\n";
    cpp << "    };\n\n";


    cpp << "    auto fn_defineClassNative = [](JNIEnv* e, jobject, jstring jn, jbyteArray bytes, jobject ldr) -> jclass {\n";
    cpp << "        if (!bytes) return nullptr;\n";
    cpp << "        jint bl = e->GetArrayLength(bytes);\n";
    cpp << "        jclass ucls = e->FindClass(\"jdk/internal/misc/Unsafe\");\n";
    cpp << "        if (!ucls) return nullptr;\n";
    cpp << "        jfieldID fid = e->GetStaticFieldID(ucls, \"theUnsafe\", \"Ljdk/internal/misc/Unsafe;\");\n";
    cpp << "        if (!fid) return nullptr;\n";
    cpp << "        jobject uf = e->GetStaticObjectField(ucls, fid);\n";
    cpp << "        jmethodID m = e->GetMethodID(ucls, \"defineClass\",\n";
    cpp << "            \"(Ljava/lang/String;[BIILjava/lang/ClassLoader;Ljava/security/ProtectionDomain;)Ljava/lang/Class;\");\n";
    cpp << "        if (!uf || !m) return nullptr;\n";
    cpp << "        jclass r = (jclass)e->CallObjectMethod(uf, m, jn, bytes, 0, bl,\n";
    cpp << "            ldr ? ldr : g_loader, nullptr);\n";
    cpp << "        if (e->ExceptionCheck()) return nullptr;\n";
    cpp << "        if (r) JvmtiMethodTable::ReportError(e, \"SetTag(defined class)\", JvmtiMethodTable::Protect(r));\n";
    cpp << "        return r;\n";
    cpp << "    };\n\n";

    cpp << R"CPP(
    auto fn_clearMixinCache0 = [](JNIEnv* e, jclass bridge) {
        jmethodID clear = e->GetStaticMethodID(bridge, "clearMixinCacheSafely", "()I");
        if (!clear) return;
        jint removed = e->CallStaticIntMethod(bridge, clear);
        if (e->ExceptionCheck()) {
            LOG("ERROR","G7","ClassInfo invalidation failed; exception propagated");
            return;
        }
        ++g_tc_cacheCleared;
        LOG("INFO","G7","ClassInfo invalidated=%d; canonical OBJECT preserved", removed);
    };

)CPP";


    cpp << "    auto fn_findLoadedClass0 = [](JNIEnv* e, jobject, jstring jname) -> jclass {\n";
    cpp << "        if (!jname || !g_jvm) return nullptr;\n";
    cpp << "        const char* nm = e->GetStringUTFChars(jname, nullptr);\n";
    cpp << "        if (!nm) return nullptr;\n";
    cpp << "        std::string wanted(nm); e->ReleaseStringUTFChars(jname, nm);\n";
    cpp << "        for (char& c : wanted) if (c == '.') c = '/';\n";
    cpp << "        JvmtiMethodTable::Environment ownedEnvironment(g_jvm);\n";
    cpp << "        jvmtiEnv* jt = ownedEnvironment.get();\n";
    cpp << "        if (!jt) return nullptr;\n";
    cpp << "        jint classCount = 0; jclass* classes = nullptr;\n";
    cpp << "        if (jt->GetLoadedClasses(&classCount, &classes) != JVMTI_ERROR_NONE) return nullptr;\n";
    cpp << "        jclass result = nullptr;\n";
    cpp << "        for (jint i = 0; i < classCount && !result; i++) {\n";
    cpp << "            char* sig = nullptr;\n";
    cpp << "            if (jt->GetClassSignature(classes[i], &sig, nullptr) == JVMTI_ERROR_NONE && sig) {\n";
    cpp << "                std::string s(sig + 1);\n";
    cpp << "                if (!s.empty() && s.back() == ';') s.pop_back();\n";
    cpp << "                if (s == wanted) result = (jclass)e->NewLocalRef(classes[i]);\n";
    cpp << "                jt->Deallocate((unsigned char*)sig);\n";
    cpp << "            }\n";
    cpp << "        }\n";
    cpp << "        for (jint i = 0; i < classCount; ++i) e->DeleteLocalRef(classes[i]);\n";
    cpp << "        jt->Deallocate((unsigned char*)classes);\n";
    cpp << "        return result;\n";
    cpp << "    };\n\n";


    cpp << "    struct NM { const char* name; const char* sig; void* fn; };\n";
    cpp << "    NM methods[] = {\n";
    cpp << "        {\"getMixinResource0\",   \"(Ljava/lang/String;)[B\",                   (void*)+fn_getMixinResource0},\n";
    cpp << "        {\"listMixinResources0\", \"()[Ljava/lang/String;\",                     (void*)+fn_listMixinResources0},\n";
    cpp << "        {\"getLoadedClassBytes0\",\"(Ljava/lang/Class;)[B\",                     (void*)+fn_getLoadedClassBytes0},\n";
    cpp << "        {\"redefineClass0\",      \"(Ljava/lang/Class;[B)V\",                    (void*)+fn_redefineClass0},\n";
    cpp << "        {\"retransformClasses0\", \"([Ljava/lang/Class;)V\",                     (void*)+fn_retransformClasses0},\n";
    cpp << "        {\"defineClassNative\",   \"(Ljava/lang/String;[BLjava/lang/ClassLoader;)Ljava/lang/Class;\", (void*)+fn_defineClassNative},\n";
    cpp << "        {\"clearMixinCache0\",    \"()V\",                                        (void*)+fn_clearMixinCache0},\n";
    cpp << "        {\"findLoadedClass0\",    \"(Ljava/lang/String;)Ljava/lang/Class;\",      (void*)+fn_findLoadedClass0},\n";
    cpp << "    };\n";
    cpp << "    JNINativeMethod jnm[8];\n";
    cpp << "    for (int i = 0; i < 8; i++) jnm[i] = {(char*)methods[i].name, (char*)methods[i].sig, methods[i].fn};\n";
    cpp << "    int reg = g_env->RegisterNatives(bridgeCls, jnm, 8);\n";
    cpp << "    LOG(\"INFO\",\"Bridge\",\"RegisterNatives: %d/8 bound\", reg == 0 ? 8 : reg);\n";
    cpp << "    if (reg != 0) {\n";
    cpp << "        LOG(\"ERROR\",\"Bridge\",\"FAIL: not all natives bound вЂ” non-parity mode\");\n";
    cpp << "    }\n";
    cpp << "    g_env->DeleteLocalRef(bridgeCls);\n";
    cpp << "}\n\n";
}





void CodeGen::emitMixinServiceWrapper(std::stringstream& cpp) {





    cpp << R"RAWCODE(
static void InstallMixinServiceWrapper() {
    jclass msClass = nullptr;
    {
        jstring cn = g_env->NewStringUTF("org.spongepowered.asm.service.MixinService");
        jclass clsCls = g_env->FindClass("java/lang/Class");
        jmethodID forName = g_env->GetStaticMethodID(clsCls, "forName",
            "(Ljava/lang/String;)Ljava/lang/Class;");
        msClass = (jclass)g_env->CallStaticObjectMethod(clsCls, forName, cn);
        g_env->DeleteLocalRef(cn);
        if (g_env->ExceptionCheck() || !msClass) {
            g_env->ExceptionClear();
            LOG("WARN","MixinSvc","MixinService class not found");
            return;
        }
    }

    jobject serviceInstance = nullptr;
    {
        jclass clsCls = g_env->FindClass("java/lang/Class");
        jmethodID gdm = g_env->GetMethodID(clsCls, "getMethod",
            "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");
        jstring gsName = g_env->NewStringUTF("getService");
        jclass objArrCls = g_env->FindClass("[Ljava/lang/Class;");
        jobjectArray emptyArr = g_env->NewObjectArray(0,
            g_env->FindClass("java/lang/Class"), nullptr);
        jobject method = g_env->CallObjectMethod(msClass, gdm, gsName, emptyArr);
        g_env->DeleteLocalRef(gsName); g_env->DeleteLocalRef(emptyArr);
        if (g_env->ExceptionCheck() || !method) {
            g_env->ExceptionClear();
            LOG("ERROR","MixinSvc","getService method not found");
            return;
        }
        jclass methodCls = g_env->FindClass("java/lang/reflect/Method");
        jmethodID setAcc = g_env->GetMethodID(methodCls, "setAccessible", "(Z)V");
        jmethodID invoke = g_env->GetMethodID(methodCls, "invoke",
            "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
        g_env->CallVoidMethod(method, setAcc, JNI_TRUE);
        jobjectArray nullArgs = g_env->NewObjectArray(0,
            g_env->FindClass("java/lang/Object"), nullptr);
        serviceInstance = g_env->CallObjectMethod(method, invoke, nullptr, nullArgs);
        g_env->DeleteLocalRef(nullArgs); g_env->DeleteLocalRef(method);
        if (g_env->ExceptionCheck() || !serviceInstance) {
            g_env->ExceptionClear();
            LOG("ERROR","MixinSvc","getService() returned null");
            return;
        }
        LOG("INFO","MixinSvc","IMixinService instance obtained");
    }

    jclass wrapperClass = FindClassByLoader("mod.runtime.DllMixinServiceWrapper");
    if (!wrapperClass) {
        LOG("WARN","MixinSvc","DllMixinServiceWrapper not found вЂ” addConfiguration fallback");
        return;
    }
    LOG("INFO","MixinSvc","DllMixinServiceWrapper class found");

    jmethodID wrapperCtor = g_env->GetMethodID(wrapperClass, "<init>",
        "(Lorg/spongepowered/asm/service/IMixinService;)V");
    if (g_env->ExceptionCheck() || !wrapperCtor) {
        g_env->ExceptionClear();
        LOG("ERROR","MixinSvc","DllMixinServiceWrapper constructor not found");
        return;
    }
    jobject wrapperInstance = g_env->NewObject(wrapperClass, wrapperCtor, serviceInstance);
    if (g_env->ExceptionCheck() || !wrapperInstance) {
        g_env->ExceptionClear();
        LOG("ERROR","MixinSvc","Failed to construct DllMixinServiceWrapper");
        return;
    }
    LOG("INFO","MixinSvc","DllMixinServiceWrapper instance created");

    {
        jclass clsCls = g_env->FindClass("java/lang/Class");
        jmethodID gdf = g_env->GetMethodID(clsCls, "getDeclaredField",
            "(Ljava/lang/String;)Ljava/lang/reflect/Field;");
        jmethodID setStatic = nullptr;
        jmethodID getField = nullptr;
        jclass fieldCls = g_env->FindClass("java/lang/reflect/Field");
        if (fieldCls) {
            setStatic = g_env->GetMethodID(fieldCls, "set", "(Ljava/lang/Object;Ljava/lang/Object;)V");
            jmethodID setAcc = g_env->GetMethodID(fieldCls, "setAccessible", "(Z)V");
            jstring in = g_env->NewStringUTF("instance");
            jobject instanceField = g_env->CallObjectMethod(msClass, gdf, in);
            g_env->DeleteLocalRef(in);
            if (instanceField && !g_env->ExceptionCheck()) {
                g_env->CallVoidMethod(instanceField, setAcc, JNI_TRUE);
                jobject serviceManager = g_env->CallObjectMethod(instanceField,
                    g_env->GetMethodID(fieldCls, "get", "(Ljava/lang/Object;)Ljava/lang/Object;"), nullptr);
                g_env->DeleteLocalRef(instanceField);
                if (serviceManager && !g_env->ExceptionCheck()) {
                    jstring sn = g_env->NewStringUTF("service");
                    jobject serviceField = g_env->CallObjectMethod(msClass, gdf, sn);
                    g_env->DeleteLocalRef(sn);
                    if (serviceField && !g_env->ExceptionCheck()) {
                        g_env->CallVoidMethod(serviceField, setAcc, JNI_TRUE);
                        if (setStatic) g_env->CallVoidMethod(serviceField, setStatic, serviceManager, wrapperInstance);
                        if (g_env->ExceptionCheck()) {
                            g_env->ExceptionClear();
                            LOG("WARN","MixinSvc","MixinService.service replacement failed");
                        } else {
                            LOG("INFO","MixinSvc","MixinService.service replaced with DLL wrapper");
                        }
                        g_env->DeleteLocalRef(serviceField);
                    } else { g_env->ExceptionClear(); }
                    g_env->DeleteLocalRef(serviceManager);
                } else { g_env->ExceptionClear(); }
            } else { g_env->ExceptionClear(); }
        }
    }

    {
        jclass clsCls = g_env->FindClass("java/lang/Class");
        jmethodID gdf = g_env->GetMethodID(clsCls, "getDeclaredField",
            "(Ljava/lang/String;)Ljava/lang/reflect/Field;");
        jstring fn = g_env->NewStringUTF("delegate");
        jobject field = g_env->CallObjectMethod(wrapperClass, gdf, fn);
        g_env->DeleteLocalRef(fn);
        if (!g_env->ExceptionCheck() && field) {
            jclass fieldCls = g_env->FindClass("java/lang/reflect/Field");
            jmethodID setAcc = g_env->GetMethodID(fieldCls, "setAccessible", "(Z)V");
            g_env->CallVoidMethod(field, setAcc, JNI_TRUE);
            if (g_env->ExceptionCheck()) g_env->ExceptionClear();
            g_env->DeleteLocalRef(field);
            LOG("INFO","MixinSvc","delegate field verified");
        } else {
            g_env->ExceptionClear();
        }
    }

    {
        jclass nbClass = FindClassByLoader("mod.runtime.NativeBridge");
        if (nbClass) {
            jclass clsCls = g_env->FindClass("java/lang/Class");
            jmethodID gdms = g_env->GetMethodID(clsCls, "getDeclaredMethods",
                "()[Ljava/lang/reflect/Method;");
            jobjectArray methods = (jobjectArray)g_env->CallObjectMethod(nbClass, gdms);
            if (!g_env->ExceptionCheck() && methods) {
                jint cnt = g_env->GetArrayLength(methods);
                int nativeCount = 0;
                jclass methodCls = g_env->FindClass("java/lang/reflect/Method");
                jmethodID getName = g_env->GetMethodID(methodCls, "getName",
                    "()Ljava/lang/String;");
                for (jint i = 0; i < cnt; i++) {
                    jobject m = g_env->GetObjectArrayElement(methods, i);
                    if (m) {
                        jstring nm = (jstring)g_env->CallObjectMethod(m, getName);
                        const char* nmStr = g_env->GetStringUTFChars(nm, nullptr);
                        bool isNative = strstr(nmStr, "0") != nullptr;
                        if (isNative) nativeCount++;
                        g_env->ReleaseStringUTFChars(nm, nmStr);
                        g_env->DeleteLocalRef(nm);
                        g_env->DeleteLocalRef(m);
                    }
                }
                g_env->DeleteLocalRef(methods);
                LOG("INFO","MixinSvc","NativeBridge methods verified: %d", nativeCount);
            } else { g_env->ExceptionClear(); }
            g_env->DeleteLocalRef(nbClass);
        }
    }

    {
        jclass envClass = FindClassByLoader("org.spongepowered.asm.mixin.MixinEnvironment");
        if (envClass) {
            jclass clsCls = g_env->FindClass("java/lang/Class");
            jmethodID gdm = g_env->GetMethodID(clsCls, "getDeclaredMethod",
                "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");
            jstring gde_name = g_env->NewStringUTF("getDefaultEnvironment");
            jobjectArray emptyArr = g_env->NewObjectArray(0,
                g_env->FindClass("java/lang/Class"), nullptr);
            jobject gdeMethod = g_env->CallObjectMethod(envClass, gdm, gde_name, emptyArr);
            g_env->DeleteLocalRef(gde_name); g_env->DeleteLocalRef(emptyArr);

            if (!g_env->ExceptionCheck() && gdeMethod) {
                jclass methodCls = g_env->FindClass("java/lang/reflect/Method");
                jmethodID setAcc = g_env->GetMethodID(methodCls, "setAccessible", "(Z)V");
                jmethodID invoke = g_env->GetMethodID(methodCls, "invoke",
                    "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
                g_env->CallVoidMethod(gdeMethod, setAcc, JNI_TRUE);
                jobjectArray nullArgs = g_env->NewObjectArray(0,
                    g_env->FindClass("java/lang/Object"), nullptr);
                jobject envInst = g_env->CallObjectMethod(gdeMethod, invoke, nullptr, nullArgs);
                g_env->DeleteLocalRef(nullArgs); g_env->DeleteLocalRef(gdeMethod);

                if (!g_env->ExceptionCheck() && envInst) {
                    jclass envInstCls = g_env->GetObjectClass(envInst);
                    jstring gat_name = g_env->NewStringUTF("getActiveTransformer");
                    jobjectArray emptyArr2 = g_env->NewObjectArray(0,
                        g_env->FindClass("java/lang/Class"), nullptr);
                    jobject gatMethod = g_env->CallObjectMethod(envInstCls, gdm,
                        gat_name, emptyArr2);
                    g_env->DeleteLocalRef(gat_name); g_env->DeleteLocalRef(emptyArr2);
                    if (g_env->ExceptionCheck()) g_env->ExceptionClear();

                    if (gatMethod) {
                        g_env->CallVoidMethod(gatMethod, setAcc, JNI_TRUE);
                        if (g_env->ExceptionCheck()) g_env->ExceptionClear();
                        jobjectArray nullArgs2 = g_env->NewObjectArray(0,
                            g_env->FindClass("java/lang/Object"), nullptr);
                        jobject transformer = g_env->CallObjectMethod(
                            gatMethod, invoke, envInst, nullArgs2);
                        g_env->DeleteLocalRef(nullArgs2); g_env->DeleteLocalRef(gatMethod);

                        if (!g_env->ExceptionCheck() && transformer) {
                            jclass transformerCls = g_env->GetObjectClass(transformer);
                            jmethodID gdms2 = g_env->GetMethodID(clsCls, "getDeclaredMethods",
                                "()[Ljava/lang/reflect/Method;");
                            jobjectArray tfMethods = (jobjectArray)g_env->CallObjectMethod(
                                transformerCls, gdms2);
                            if (!g_env->ExceptionCheck() && tfMethods) {
                                jint mcount = g_env->GetArrayLength(tfMethods);
                                LOG("INFO","MixinSvc","Transformer has %d methods", (int)mcount);
                                g_env->DeleteLocalRef(tfMethods);
                            } else { g_env->ExceptionClear(); }

                            jstring gdfield = g_env->NewStringUTF("getDeclaredFields");
                            jmethodID gdfm = g_env->GetMethodID(clsCls, "getDeclaredFields",
                                "()[Ljava/lang/reflect/Field;");
                            jobjectArray fields = (jobjectArray)g_env->CallObjectMethod(
                                transformerCls, gdfm);
                            if (!g_env->ExceptionCheck() && fields) {
                                jint fcount = g_env->GetArrayLength(fields);
                                jclass fieldCls = g_env->FindClass("java/lang/reflect/Field");
                                jmethodID setAcc2 = g_env->GetMethodID(fieldCls,
                                    "setAccessible", "(Z)V");
                                jmethodID fieldGet = g_env->GetMethodID(fieldCls,
                                    "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
                                jclass listCls = g_env->FindClass("java/util/List");

                                for (jint fi = 0; fi < fcount; fi++) {
                                    jobject field = g_env->GetObjectArrayElement(fields, fi);
                                    if (!field) continue;
                                    g_env->CallVoidMethod(field, setAcc2, JNI_TRUE);
                                    if (g_env->ExceptionCheck()) {
                                        g_env->ExceptionClear();
                                        g_env->DeleteLocalRef(field);
                                        continue;
                                    }
                                    jobject fval = g_env->CallObjectMethod(
                                        field, fieldGet, transformer);
                                    if (g_env->ExceptionCheck()) {
                                        g_env->ExceptionClear();
                                        g_env->DeleteLocalRef(field);
                                        continue;
                                    }
                                    if (fval && g_env->IsInstanceOf(fval, listCls)) {
                                        jmethodID isEmpty = g_env->GetMethodID(listCls,
                                            "isEmpty", "()Z");
                                        jboolean empty = g_env->CallBooleanMethod(fval, isEmpty);
                                        if (!g_env->ExceptionCheck() && !empty) {
                                            jmethodID clear = g_env->GetMethodID(listCls,
                                                "clear", "()V");
                                            g_env->CallVoidMethod(fval, clear);
                                            if (g_env->ExceptionCheck()) {
                                                g_env->ExceptionClear();
                                            } else {
                                                LOG("INFO","MixinSvc",
                                                    "Transformer config list cleared (internal path)");
                                            }
                                        } else { g_env->ExceptionClear(); }
                                        g_env->DeleteLocalRef(fval);
                                    } else if (fval) { g_env->DeleteLocalRef(fval); }
                                    g_env->DeleteLocalRef(field);
                                }
                                g_env->DeleteLocalRef(fields);
                                g_env->DeleteLocalRef(gdfield);
                            } else { g_env->ExceptionClear(); }
                            g_env->DeleteLocalRef(transformer);
                            g_env->DeleteLocalRef(transformerCls);
                        } else { g_env->ExceptionClear(); }
                    }
                    g_env->DeleteLocalRef(envInstCls);
                    g_env->DeleteLocalRef(envInst);
                } else { g_env->ExceptionClear(); }
            } else { g_env->ExceptionClear(); }
            g_env->DeleteLocalRef(envClass);
        }
    }

    {
        jclass svcCls = g_env->GetObjectClass(serviceInstance);
        jmethodID offerMethod = g_env->GetMethodID(svcCls, "offer",
            "(Lorg/spongepowered/asm/service/IMixinInternal;)V");
        if (g_env->ExceptionCheck()) g_env->ExceptionClear();
        if (offerMethod) {
            g_env->CallVoidMethod(serviceInstance, offerMethod, wrapperInstance);
            if (g_env->ExceptionCheck()) {
                g_env->ExceptionClear();
                LOG("WARN","MixinSvc","offer() failed вЂ” wrapper not installed through offer()");
            } else {
                LOG("INFO","MixinSvc","DllMixinServiceWrapper installed");
            }
        }

        jclass threadCls = g_env->FindClass("java/lang/Thread");
        jmethodID ctm = g_env->GetStaticMethodID(threadCls, "currentThread",
            "()Ljava/lang/Thread;");
        jmethodID setTCCL = g_env->GetMethodID(threadCls, "setContextClassLoader",
            "(Ljava/lang/ClassLoader;)V");
        jobject curThread = g_env->CallStaticObjectMethod(threadCls, ctm);
        if (curThread && g_loader && setTCCL) {
            g_env->CallVoidMethod(curThread, setTCCL, g_loader);
            if (g_env->ExceptionCheck()) g_env->ExceptionClear();
            else LOG("INFO","MixinSvc","TCCL set to KnotClassLoader");
        }
        if (curThread) g_env->DeleteLocalRef(curThread);
        g_env->DeleteLocalRef(svcCls);
    }

    if (wrapperInstance) g_env->DeleteLocalRef(wrapperInstance);
    if (serviceInstance) g_env->DeleteLocalRef(serviceInstance);
    if (wrapperClass) g_env->DeleteLocalRef(wrapperClass);
    if (msClass) g_env->DeleteLocalRef(msClass);
}

)RAWCODE";
}

void CodeGen::emitActiveMixinConnector(std::stringstream& cpp, const CodeGenConfig& config) {





    cpp << "static std::vector<std::string> g_mixinConfigs = {\n";
    for (const auto& cfg : config.mixinConfigs) {
        cpp << "    \"" << cfg << "\",\n";
    }
    cpp << "};\n";
    cpp << "static std::vector<std::string> g_embeddedMixinClasses = {\n";
    for (const auto& info : config.mixinConfigInfos) {
        std::string package = info.packageName;
        if (!package.empty() && package.back() != '.') package += '.';
        for (const auto& simple : info.commonMixins)
            cpp << "    \"" << package << simple << "\",\n";
        for (const auto& simple : info.clientMixins)
            cpp << "    \"" << package << simple << "\",\n";
    }
    cpp << "};\n";
    cpp << "static std::vector<std::string> g_adapterMixinPackages = {\n";
    for (const auto& info : config.mixinConfigInfos) {
        if (!info.packageName.empty()) cpp << "    \"" << info.packageName << "\",\n";
    }
    cpp << "};\n";
    cpp << "static bool g_internalConfigInjected = false;\n\n";

    cpp << R"RAWCODE(
static void InjectMixinConfigsInternal() {
    jclass bridge = FindClassByLoader("mod.runtime.NativeBridge");
    if (!bridge) return;
    jmethodID prepare = g_env->GetStaticMethodID(bridge, "prepareLateConfigs", "([Ljava/lang/String;)V");
    jclass strings = g_env->FindClass("java/lang/String");
    jobjectArray names = strings ? g_env->NewObjectArray((jsize)g_mixinConfigs.size(), strings, nullptr) : nullptr;
    for (jsize i = 0; names && i < (jsize)g_mixinConfigs.size() && !g_env->ExceptionCheck(); ++i) {
        jstring name = g_env->NewStringUTF(g_mixinConfigs[i].c_str());
        if (name) { g_env->SetObjectArrayElement(names, i, name); g_env->DeleteLocalRef(name); }
    }
    if (prepare && names && !g_env->ExceptionCheck())
        g_env->CallStaticVoidMethod(bridge, prepare, names);
    if (names) g_env->DeleteLocalRef(names);
    if (strings) g_env->DeleteLocalRef(strings);
    g_env->DeleteLocalRef(bridge);
    if (g_env->ExceptionCheck()) {
        LOG("ERROR","MixinCfg","Config preparation failed before capture");
        return;
    }
    g_internalConfigInjected = true;
    LOG("INFO","MixinCfg","onLoad/onSelect/prepareConfigs complete; configs=%zu", g_mixinConfigs.size());
}

static bool IsEmbeddedMixinConfig(JNIEnv* env, jobject configApi) {
    if (!env || !configApi) return false;
    jclass apiCls = env->GetObjectClass(configApi);
    jmethodID getName = apiCls ? env->GetMethodID(apiCls, "getName", "()Ljava/lang/String;") : nullptr;
    jstring name = getName ? (jstring)env->CallObjectMethod(configApi, getName) : nullptr;
    if (env->ExceptionCheck()) { env->ExceptionClear(); name = nullptr; }
    bool match = false;
    if (name) {
        const char* value = env->GetStringUTFChars(name, nullptr);
        if (value) {
            for (const auto& wanted : g_mixinConfigs) {
                if (wanted == value || std::string(value).ends_with("/" + wanted)) { match = true; break; }
            }
            env->ReleaseStringUTFChars(name, value);
        }
        env->DeleteLocalRef(name);
    }
    if (apiCls) env->DeleteLocalRef(apiCls);
    return match;
}

static bool IsEmbeddedMixinConfigName(const char* name) {
    if (!name) return false;
    for (const auto& wanted : g_mixinConfigs) {
        if (wanted == name || std::string(name).ends_with("/" + wanted)) return true;
    }
    return false;
}

static void DirectTransformAndRedefineTargets() {
    jvmtiEnv* jt = g_jvmti;
    if (!g_jvm) return;
    if (!jt) return;


    jclass mixinAnn = FindClassByLoader("org.spongepowered.asm.mixin.Mixin");
    jclass clsCls   = g_env->FindClass("java/lang/Class");
    if (!mixinAnn || !clsCls) return;

    jmethodID iap = g_env->GetMethodID(clsCls, "isAnnotationPresent",
        "(Ljava/lang/Class;)Z");
    jmethodID ga  = g_env->GetMethodID(clsCls, "getAnnotation",
        "(Ljava/lang/Class;)Ljava/lang/annotation/Annotation;");
    jmethodID va  = g_env->GetMethodID(mixinAnn, "value", "()[Ljava/lang/Class;");
    jmethodID ta  = g_env->GetMethodID(mixinAnn, "targets", "()[Ljava/lang/String;");
    if (g_env->ExceptionCheck()) g_env->ExceptionClear();

    std::vector<jclass> targets;

    {
        jclass bridge = FindClassByLoader("mod.runtime.NativeBridge");
        jmethodID readTargets = bridge ? g_env->GetStaticMethodID(bridge,
            "getMixinTargetNames0", "(Ljava/lang/Class;)[Ljava/lang/String;") : nullptr;
        jclass stringCls = g_env->FindClass("java/lang/String");
        if (readTargets && stringCls) {
            for (const auto& mixinName : g_embeddedMixinClasses) {
                std::string internal = mixinName;
                std::replace(internal.begin(), internal.end(), '.', '/');
                auto loaded = g_classes.find(internal);
                if (loaded == g_classes.end()) loaded = g_classes.find(mixinName);
                if (loaded == g_classes.end() || !loaded->second) continue;
                jobjectArray names = (jobjectArray)g_env->CallStaticObjectMethod(
                    bridge, readTargets, loaded->second);
                if (g_env->ExceptionCheck()) {
                    LOG("ERROR","G6","Cannot read embedded mixin targets: %s; aborting transform pass", mixinName.c_str());
                    g_env->DeleteLocalRef(bridge);
                    return;
                }
                for (jsize i = 0; names && i < g_env->GetArrayLength(names); ++i) {
                    jstring name = (jstring)g_env->GetObjectArrayElement(names, i);
                    if (!name) continue;
                    const char* text = g_env->GetStringUTFChars(name, nullptr);
                    if (text) {
                        jclass target = FindLoadedClassNoLoad(text);
                        if (target) { targets.push_back(target); ++g_tc_mixinTargetsFound; }
                        g_env->ReleaseStringUTFChars(name, text);
                    }
                    g_env->DeleteLocalRef(name);
                }
                if (names) g_env->DeleteLocalRef(names);
            }
        }
        if (bridge) g_env->DeleteLocalRef(bridge);
    }

    jclass configCls = FindClassByLoader("org.spongepowered.asm.mixin.transformer.Config");
    jclass mixinConfigCls = FindClassByLoader("org.spongepowered.asm.mixin.extensibility.IMixinConfig");
    jclass setCls = g_env->FindClass("java/util/Set");
    jclass iteratorCls = g_env->FindClass("java/util/Iterator");
    jclass mixinsCls = FindClassByLoader("org.spongepowered.asm.mixin.Mixins");
    if (targets.empty() && configCls && mixinConfigCls && setCls && iteratorCls && mixinsCls) {
        jmethodID getConfigs = g_env->GetStaticMethodID(mixinsCls, "getConfigs", "()Ljava/util/Set;");
        jmethodID getConfig = g_env->GetMethodID(configCls, "getConfig",
            "()Lorg/spongepowered/asm/mixin/extensibility/IMixinConfig;");
        jmethodID getTargets = g_env->GetMethodID(mixinConfigCls, "getTargets",
            "()Ljava/util/Set;");
        jmethodID setIterator = g_env->GetMethodID(setCls, "iterator",
            "()Ljava/util/Iterator;");
        jmethodID hasNext = g_env->GetMethodID(iteratorCls, "hasNext", "()Z");
        jmethodID next = g_env->GetMethodID(iteratorCls, "next", "()Ljava/lang/Object;");
        if (getConfigs && getConfig && getTargets && setIterator && hasNext && next) {
            jobject handles = g_env->CallStaticObjectMethod(mixinsCls, getConfigs);
            jobject itHandles = handles ? g_env->CallObjectMethod(handles, setIterator) : nullptr;
            while (itHandles && !g_env->ExceptionCheck() && g_env->CallBooleanMethod(itHandles, hasNext)) {
                jobject handle = g_env->CallObjectMethod(itHandles, next);
                if (g_env->ExceptionCheck()) { g_env->ExceptionClear(); continue; }
                if (!handle) continue;
                jobject cfgApi = g_env->CallObjectMethod(handle, getConfig);
                if (g_env->ExceptionCheck() || !cfgApi) {
                    g_env->ExceptionClear(); g_env->DeleteLocalRef(handle); continue;
                }
                if (!IsEmbeddedMixinConfig(g_env, cfgApi)) {
                    g_env->DeleteLocalRef(cfgApi);
                    g_env->DeleteLocalRef(handle);
                    continue;
                }
                jobject targetSet = g_env->CallObjectMethod(cfgApi, getTargets);
                if (g_env->ExceptionCheck() || !targetSet) {
                    g_env->ExceptionClear(); g_env->DeleteLocalRef(cfgApi);
                    g_env->DeleteLocalRef(handle); continue;
                }
                jobject it = g_env->CallObjectMethod(targetSet, setIterator);
                while (it && !g_env->ExceptionCheck() &&
                       g_env->CallBooleanMethod(it, hasNext)) {
                    jobject nameObj = g_env->CallObjectMethod(it, next);
                    if (g_env->ExceptionCheck() || !nameObj) { g_env->ExceptionClear(); break; }
                    jstring name = (jstring)nameObj;
                    const char* utf = g_env->GetStringUTFChars(name, nullptr);
                    if (utf) {
                        jclass target = FindLoadedClassNoLoad(utf);
                        if (target) { targets.push_back(target); ++g_tc_mixinTargetsFound; }
                        g_env->ReleaseStringUTFChars(name, utf);
                    }
                    g_env->DeleteLocalRef(nameObj);
                }
                if (g_env->ExceptionCheck()) g_env->ExceptionClear();
                if (it) g_env->DeleteLocalRef(it);
                g_env->DeleteLocalRef(targetSet);
                g_env->DeleteLocalRef(cfgApi);
                g_env->DeleteLocalRef(handle);
            }
            if (g_env->ExceptionCheck()) g_env->ExceptionClear();
            if (itHandles) g_env->DeleteLocalRef(itHandles);
            if (handles) g_env->DeleteLocalRef(handles);
        }
    }

    if (targets.empty()) {
        jclass env0 = FindClassByLoader("org.spongepowered.asm.mixin.MixinEnvironment");
        jclass list0 = g_env->FindClass("java/util/List");
        if (env0 && list0 && configCls && mixinConfigCls && setCls && iteratorCls) {
            jmethodID def0 = g_env->GetStaticMethodID(env0, "getDefaultEnvironment",
                "()Lorg/spongepowered/asm/mixin/MixinEnvironment;");
            jmethodID names0 = g_env->GetMethodID(env0, "getMixinConfigs", "()Ljava/util/List;");
            jmethodID create0 = g_env->GetStaticMethodID(configCls, "create",
                "(Ljava/lang/String;Lorg/spongepowered/asm/mixin/MixinEnvironment;)Lorg/spongepowered/asm/mixin/transformer/Config;");
            jmethodID size0 = g_env->GetMethodID(list0, "size", "()I");
            jmethodID get0 = g_env->GetMethodID(list0, "get", "(I)Ljava/lang/Object;");
            jmethodID cfg0 = g_env->GetMethodID(configCls, "getConfig",
                "()Lorg/spongepowered/asm/mixin/extensibility/IMixinConfig;");
            jmethodID tar0 = g_env->GetMethodID(mixinConfigCls, "getTargets", "()Ljava/util/Set;");
            jmethodID sit0 = g_env->GetMethodID(setCls, "iterator", "()Ljava/util/Iterator;");
            jmethodID hnext0 = g_env->GetMethodID(iteratorCls, "hasNext", "()Z");
            jmethodID next0 = g_env->GetMethodID(iteratorCls, "next", "()Ljava/lang/Object;");
            if (def0 && names0 && create0 && size0 && get0 && cfg0 && tar0 && sit0 && hnext0 && next0) {
                jobject e0 = g_env->CallStaticObjectMethod(env0, def0);
                jobject ns0 = e0 ? g_env->CallObjectMethod(e0, names0) : nullptr;
                jint nn0 = (ns0 && !g_env->ExceptionCheck()) ? g_env->CallIntMethod(ns0, size0) : 0;
                if (g_env->ExceptionCheck()) { g_env->ExceptionClear(); nn0 = 0; }
                LOG("INFO","JVMTI","Config-name fallback count=%d", (int)nn0);
                for (jint i0 = 0; ns0 && i0 < nn0 && !g_env->ExceptionCheck(); ++i0) {
                    jobject no0 = g_env->CallObjectMethod(ns0, get0, i0);
                    if (g_env->ExceptionCheck()) { g_env->ExceptionClear(); continue; }
                    if (!no0) continue;
                    const char* configName = g_env->GetStringUTFChars((jstring)no0, nullptr);
                    bool embedded = IsEmbeddedMixinConfigName(configName);
                    if (configName) g_env->ReleaseStringUTFChars((jstring)no0, configName);
                    if (!embedded) { g_env->DeleteLocalRef(no0); continue; }
                    jobject h0 = g_env->CallStaticObjectMethod(configCls, create0, no0, e0);
                    if (!g_env->ExceptionCheck() && h0) {
                        jobject a0 = g_env->CallObjectMethod(h0, cfg0);
                        jobject s0 = (!g_env->ExceptionCheck() && a0) ? g_env->CallObjectMethod(a0, tar0) : nullptr;
                        jobject z0 = (!g_env->ExceptionCheck() && s0) ? g_env->CallObjectMethod(s0, sit0) : nullptr;
                        while (z0 && !g_env->ExceptionCheck() && g_env->CallBooleanMethod(z0, hnext0)) {
                            jobject tn0 = g_env->CallObjectMethod(z0, next0);
                            if (g_env->ExceptionCheck()) { g_env->ExceptionClear(); break; }
                            if (!tn0) continue;
                            const char* u0 = g_env->GetStringUTFChars((jstring)tn0, nullptr);
                            if (u0) {
                                jclass t0 = FindLoadedClassNoLoad(u0);
                                if (t0) { targets.push_back(t0); ++g_tc_mixinTargetsFound; }
                                g_env->ReleaseStringUTFChars((jstring)tn0, u0);
                            }
                            g_env->DeleteLocalRef(tn0);
                        }
                        if (g_env->ExceptionCheck()) g_env->ExceptionClear();
                        if (z0) g_env->DeleteLocalRef(z0);
                        if (s0) g_env->DeleteLocalRef(s0);
                        if (a0) g_env->DeleteLocalRef(a0);
                        g_env->DeleteLocalRef(h0);
                    } else if (g_env->ExceptionCheck()) g_env->ExceptionClear();
                    g_env->DeleteLocalRef(no0);
                }
                if (ns0) g_env->DeleteLocalRef(ns0);
                if (e0) g_env->DeleteLocalRef(e0);
            } else if (g_env->ExceptionCheck()) g_env->ExceptionClear();
        }
        if (env0) g_env->DeleteLocalRef(env0);
        if (list0) g_env->DeleteLocalRef(list0);
    }

    for (auto& kv : g_classes) {
        jclass cls = kv.second;
        if (!cls || !va) continue;
        jboolean has = g_env->CallBooleanMethod(cls, iap, mixinAnn);
        if (g_env->ExceptionCheck()) { g_env->ExceptionClear(); continue; }
        if (!has) continue;
        jobject ann = g_env->CallObjectMethod(cls, ga, mixinAnn);
        if (g_env->ExceptionCheck() || !ann) { g_env->ExceptionClear(); continue; }

        if (va) {
            jobjectArray vals = (jobjectArray)g_env->CallObjectMethod(ann, va);
            if (!g_env->ExceptionCheck() && vals) {
                jint vl = g_env->GetArrayLength(vals);
                for (jint i = 0; i < vl; i++) {
                    jclass t = (jclass)g_env->GetObjectArrayElement(vals, i);
                    if (t) { targets.push_back(t); ++g_tc_mixinTargetsFound; }
                }
                g_env->DeleteLocalRef(vals);
            } else { g_env->ExceptionClear(); }
        }

        if (ta) {
            jobjectArray ts = (jobjectArray)g_env->CallObjectMethod(ann, ta);
            if (!g_env->ExceptionCheck() && ts) {
                jint tl = g_env->GetArrayLength(ts);
                for (jint i = 0; i < tl; i++) {
                    jstring tn = (jstring)g_env->GetObjectArrayElement(ts, i);
                    if (!tn) continue;
                    const char* tc = g_env->GetStringUTFChars(tn, nullptr);
                    jclass t = FindLoadedClassNoLoad(tc);
                    g_env->ReleaseStringUTFChars(tn, tc);
                    g_env->DeleteLocalRef(tn);
                    if (t) { targets.push_back(t); ++g_tc_mixinTargetsFound; }
                }
                g_env->DeleteLocalRef(ts);
            } else { g_env->ExceptionClear(); }
        }
        g_env->DeleteLocalRef(ann);
    }
    if (mixinAnn) g_env->DeleteLocalRef(mixinAnn);

    {
        std::vector<jclass> unique;
        for (auto c : targets) {
            bool dup = false;
            for (auto e : unique) if (g_env->IsSameObject(c,e)) { dup=true; break; }
            if (!dup) unique.push_back(c);
        }
        targets = unique;
    }
    LOG("INFO","JVMTI","%d unique mixin targets", (int)targets.size());
    if (targets.empty()) {
        LOG("INFO","JVMTI","annotation scan empty; using prepared processor configs");
    }

    jclass envCls = FindClassByLoader("org.spongepowered.asm.mixin.MixinEnvironment");
    jobject transformer = nullptr;
    if (envCls) {
        jclass clsCls2 = g_env->FindClass("java/lang/Class");
        jmethodID gdm = g_env->GetMethodID(clsCls2, "getDeclaredMethod",
            "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");
        jclass methodCls = g_env->FindClass("java/lang/reflect/Method");
        jmethodID sa2 = g_env->GetMethodID(methodCls, "setAccessible", "(Z)V");
        jmethodID inv2 = g_env->GetMethodID(methodCls, "invoke",
            "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
        jobjectArray eArr = g_env->NewObjectArray(0, clsCls2, nullptr);
        jobjectArray oArr = g_env->NewObjectArray(0, g_env->FindClass("java/lang/Object"), nullptr);

        jstring gde_name = g_env->NewStringUTF("getDefaultEnvironment");
        jobject gdeM = g_env->CallObjectMethod(envCls, gdm, gde_name, eArr);
        g_env->DeleteLocalRef(gde_name);
        if (!g_env->ExceptionCheck() && gdeM) {
            g_env->CallVoidMethod(gdeM, sa2, JNI_TRUE);
            if (g_env->ExceptionCheck()) g_env->ExceptionClear();
            jobject envInst = g_env->CallObjectMethod(gdeM, inv2, nullptr, oArr);
            g_env->DeleteLocalRef(gdeM);
            if (!g_env->ExceptionCheck() && envInst) {
                jclass eic = g_env->GetObjectClass(envInst);
                jstring gat_n = g_env->NewStringUTF("getActiveTransformer");
                jobject gatM = g_env->CallObjectMethod(eic, gdm, gat_n, eArr);
                g_env->DeleteLocalRef(gat_n); g_env->DeleteLocalRef(eic);
                if (!g_env->ExceptionCheck() && gatM) {
                    g_env->CallVoidMethod(gatM, sa2, JNI_TRUE);
                    if (g_env->ExceptionCheck()) g_env->ExceptionClear();
                    jobject oArr2 = g_env->NewObjectArray(0,
                        g_env->FindClass("java/lang/Object"), nullptr);
                    transformer = g_env->CallObjectMethod(gatM, inv2, envInst, oArr2);
                    g_env->DeleteLocalRef(oArr2); g_env->DeleteLocalRef(gatM);
                    if (g_env->ExceptionCheck()) { g_env->ExceptionClear(); transformer = nullptr; }
                } else { g_env->ExceptionClear(); }
                g_env->DeleteLocalRef(envInst);
            } else { g_env->ExceptionClear(); }
        } else { g_env->ExceptionClear(); }
        g_env->DeleteLocalRef(eArr); g_env->DeleteLocalRef(oArr);
        g_env->DeleteLocalRef(envCls);
    }

    jobject transformMethod = nullptr;
    jclass transformerCls  = nullptr;
    if (transformer) {
        transformerCls = g_env->GetObjectClass(transformer);
        jclass clsCls3 = g_env->FindClass("java/lang/Class");
        jmethodID gcn3 = clsCls3 ? g_env->GetMethodID(clsCls3, "getName", "()Ljava/lang/String;") : nullptr;
        jstring transformerName3 = transformerCls && gcn3 ? (jstring)g_env->CallObjectMethod(transformerCls, gcn3) : nullptr;
        const char* transformerNameUtf3 = transformerName3 ? g_env->GetStringUTFChars(transformerName3, nullptr) : nullptr;
        LOG("INFO","G6","active transformer class=%s", transformerNameUtf3 ? transformerNameUtf3 : "<unknown>");
        if (transformerNameUtf3) g_env->ReleaseStringUTFChars(transformerName3, transformerNameUtf3);
        if (transformerName3) g_env->DeleteLocalRef(transformerName3);
        jmethodID gdms3 = g_env->GetMethodID(clsCls3, "getDeclaredMethods",
            "()[Ljava/lang/reflect/Method;");
        jobjectArray mArr = (jobjectArray)g_env->CallObjectMethod(transformerCls, gdms3);
        if (!g_env->ExceptionCheck() && mArr) {
            jclass methodCls3 = g_env->FindClass("java/lang/reflect/Method");
            jmethodID gName3  = g_env->GetMethodID(methodCls3, "getName", "()Ljava/lang/String;");
            jmethodID gPC3    = g_env->GetMethodID(methodCls3, "getParameterCount", "()I");
            jmethodID sa3     = g_env->GetMethodID(methodCls3, "setAccessible", "(Z)V");
            jint mc3 = g_env->GetArrayLength(mArr);
            for (jint mi = 0; mi < mc3 && !transformMethod; mi++) {
                jobject m3 = g_env->GetObjectArrayElement(mArr, mi);
                if (!m3) continue;
                jint pc3 = g_env->CallIntMethod(m3, gPC3);
                if (g_env->ExceptionCheck()) { g_env->ExceptionClear(); g_env->DeleteLocalRef(m3); continue; }
                if (pc3 != 3) { g_env->DeleteLocalRef(m3); continue; }
                jstring mn3 = (jstring)g_env->CallObjectMethod(m3, gName3);
                if (g_env->ExceptionCheck()) { g_env->ExceptionClear(); g_env->DeleteLocalRef(m3); continue; }
                const char* mnc3 = g_env->GetStringUTFChars(mn3, nullptr);
                LOG("INFO","G6","transformer method=%s params=%d", mnc3 ? mnc3 : "<unknown>", (int)pc3);
                bool isTransform = mnc3 && strcmp(mnc3, "transformClassBytes") == 0;
                g_env->ReleaseStringUTFChars(mn3, mnc3);
                g_env->DeleteLocalRef(mn3);
                if (!isTransform) { g_env->DeleteLocalRef(m3); continue; }
                g_env->CallVoidMethod(m3, sa3, JNI_TRUE);
                if (g_env->ExceptionCheck()) {
                    LOG("WARN","G6","setAccessible(transform method) failed; using public method");
                    g_env->ExceptionClear();
                }
                transformMethod = m3;
                jmethodID gpt3 = g_env->GetMethodID(methodCls3, "getParameterTypes", "()[Ljava/lang/Class;");
                jobjectArray pts3 = gpt3 ? (jobjectArray)g_env->CallObjectMethod(m3, gpt3) : nullptr;
                std::string sig3;
                if (!g_env->ExceptionCheck() && pts3) {
                    jmethodID gpn3 = g_env->GetMethodID(clsCls3, "getName", "()Ljava/lang/String;");
                    for (jsize pi3 = 0; pi3 < g_env->GetArrayLength(pts3); ++pi3) {
                        jobject pt3 = g_env->GetObjectArrayElement(pts3, pi3);
                        jstring ptn3 = pt3 && gpn3 ? (jstring)g_env->CallObjectMethod(pt3, gpn3) : nullptr;
                        const char* ptu3 = ptn3 ? g_env->GetStringUTFChars(ptn3, nullptr) : nullptr;
                        if (pi3) sig3 += ",";
                        sig3 += ptu3 ? ptu3 : "?";
                        if (ptu3) g_env->ReleaseStringUTFChars(ptn3, ptu3);
                        if (ptn3) g_env->DeleteLocalRef(ptn3);
                        if (pt3) g_env->DeleteLocalRef(pt3);
                    }
                    if (g_env->ExceptionCheck()) g_env->ExceptionClear();
                } else if (g_env->ExceptionCheck()) { g_env->ExceptionClear(); }
                LOG("INFO","G6","selected transform method from %d candidates params=(%s)", (int)mc3, sig3.c_str());
                if (pts3) g_env->DeleteLocalRef(pts3);
            }
            g_env->DeleteLocalRef(mArr);
        } else { g_env->ExceptionClear(); }
    }

    if (transformer && !transformMethod) {
        LOG("ERROR","G6","no three-argument transform method selected");
    }

    if (transformer && targets.empty()) {
        jclass tc = g_env->GetObjectClass(transformer);
        jclass fc = g_env->FindClass("java/lang/reflect/Field");
        jclass lc = g_env->FindClass("java/util/List");
        jclass sc = g_env->FindClass("java/util/Set");
        jclass ic = g_env->FindClass("java/util/Iterator");
        if (tc && fc && lc && sc && ic) {
            jmethodID gdf = g_env->GetMethodID(g_env->FindClass("java/lang/Class"),
                "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;");
            jmethodID fsa = g_env->GetMethodID(fc, "setAccessible", "(Z)V");
            jmethodID fget = g_env->GetMethodID(fc, "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
            jmethodID lsize = g_env->GetMethodID(lc, "size", "()I");
            jmethodID lget = g_env->GetMethodID(lc, "get", "(I)Ljava/lang/Object;");
            jmethodID getTargets2 = g_env->GetMethodID(
                FindClassByLoader("org.spongepowered.asm.mixin.transformer.MixinConfig"),
                "getTargets", "()Ljava/util/Set;");
            jmethodID siter = g_env->GetMethodID(sc, "iterator", "()Ljava/util/Iterator;");
            jmethodID ihas = g_env->GetMethodID(ic, "hasNext", "()Z");
            jmethodID inext = g_env->GetMethodID(ic, "next", "()Ljava/lang/Object;");
            if (gdf && fsa && fget && lsize && lget && getTargets2 && siter && ihas && inext) {
                jstring pn = g_env->NewStringUTF("processor");
                jobject pf = g_env->CallObjectMethod(tc, gdf, pn);
                g_env->DeleteLocalRef(pn);
                if (!g_env->ExceptionCheck() && pf) {
                    g_env->CallVoidMethod(pf, fsa, JNI_TRUE);
                    jobject processor = g_env->CallObjectMethod(pf, fget, transformer);
                    if (!g_env->ExceptionCheck() && processor) {
                        jclass pc = g_env->GetObjectClass(processor);
                        for (const char* fieldName : {"configs", "pendingConfigs"}) {
                            jstring cn = g_env->NewStringUTF(fieldName);
                            jobject cf = g_env->CallObjectMethod(pc, gdf, cn);
                            g_env->DeleteLocalRef(cn);
                            if (g_env->ExceptionCheck() || !cf) { g_env->ExceptionClear(); continue; }
                            g_env->CallVoidMethod(cf, fsa, JNI_TRUE);
                            jobject configList = g_env->CallObjectMethod(cf, fget, processor);
                            if (!g_env->ExceptionCheck() && configList && g_env->IsInstanceOf(configList, lc)) {
                                jint n = g_env->CallIntMethod(configList, lsize);
                                LOG("INFO","JVMTI","processor.%s count=%d", fieldName, (int)n);
                                for (jint i = 0; i < n && !g_env->ExceptionCheck(); ++i) {
                                    jobject cfg = g_env->CallObjectMethod(configList, lget, i);
                                    if (!cfg) continue;
                                    jobject set = g_env->CallObjectMethod(cfg, getTargets2);
                                    if (!g_env->ExceptionCheck() && set) {
                                        jobject it = g_env->CallObjectMethod(set, siter);
                                        while (it && !g_env->ExceptionCheck() &&
                                               g_env->CallBooleanMethod(it, ihas)) {
                                            jstring name = (jstring)g_env->CallObjectMethod(it, inext);
                                            if (!name || g_env->ExceptionCheck()) { g_env->ExceptionClear(); break; }
                                            const char* text = g_env->GetStringUTFChars(name, nullptr);
                                            if (text) {
                                                jclass target = FindLoadedClassNoLoad(text);
                                                if (target) { targets.push_back(target); ++g_tc_mixinTargetsFound; }
                                                g_env->ReleaseStringUTFChars(name, text);
                                            }
                                            g_env->DeleteLocalRef(name);
                                        }
                                        if (g_env->ExceptionCheck()) g_env->ExceptionClear();
                                        if (it) g_env->DeleteLocalRef(it);
                                        g_env->DeleteLocalRef(set);
                                    } else { g_env->ExceptionClear(); }
                                    g_env->DeleteLocalRef(cfg);
                                }
                            } else { g_env->ExceptionClear(); }
                            if (configList) g_env->DeleteLocalRef(configList);
                            g_env->DeleteLocalRef(cf);
                        }
                        g_env->DeleteLocalRef(pc);
                        g_env->DeleteLocalRef(processor);
                    } else { g_env->ExceptionClear(); }
                    g_env->DeleteLocalRef(pf);
                } else { g_env->ExceptionClear(); }
            }
        }
        if (tc) g_env->DeleteLocalRef(tc);
    }

    if (targets.empty()) return;

    jclass methodCls4 = g_env->FindClass("java/lang/reflect/Method");
    jmethodID inv4 = g_env->GetMethodID(methodCls4, "invoke",
        "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");

    for (jclass target : targets) {
        if (!target) continue;
        ++g_tc_loadedTargetsFound;

        jmethodID targetNameMid = g_env->GetMethodID(clsCls, "getName", "()Ljava/lang/String;");
        jstring targetNameObj = targetNameMid ? (jstring)g_env->CallObjectMethod(target, targetNameMid) : nullptr;
        const char* targetNameUtf = targetNameObj ? g_env->GetStringUTFChars(targetNameObj, nullptr) : nullptr;
        LOG("INFO","G6","begin target=%s index=%d/%d", targetNameUtf ? targetNameUtf : "<unknown>",
            (int)g_tc_loadedTargetsFound, (int)targets.size());

        std::vector<uint8_t> originalBytes = GetOriginalBytes(g_env, target);
        if (originalBytes.empty()) {
            if (g_env->ExceptionCheck()) return;
            LOG("WARN","G6","getLoadedClassBytes0 returned null for target");
            if (targetNameUtf) g_env->ReleaseStringUTFChars(targetNameObj, targetNameUtf);
            if (targetNameObj) g_env->DeleteLocalRef(targetNameObj);
            ++g_tc_redefineSkippedNoBytes;
            continue;
        }
        LOG("INFO","G6","input bytes=%zu", originalBytes.size());

        jbyteArray newBytesArr = nullptr;
        jclass bridge = FindClassByLoader("mod.runtime.NativeBridge");
        jmethodID transformOwned = bridge ? g_env->GetStaticMethodID(bridge, "transformOwned",
            "(Ljava/lang/Object;Ljava/lang/String;[B[Ljava/lang/String;)[B") : nullptr;
        jclass strings = g_env->FindClass("java/lang/String");
        jobjectArray owned = strings ? g_env->NewObjectArray((jsize)g_mixinConfigs.size(), strings, nullptr) : nullptr;
        for (jsize i = 0; owned && i < (jsize)g_mixinConfigs.size(); ++i) {
            jstring value = g_env->NewStringUTF(g_mixinConfigs[i].c_str());
            g_env->SetObjectArrayElement(owned, i, value);
            g_env->DeleteLocalRef(value);
        }
        jbyteArray input = g_env->NewByteArray((jsize)originalBytes.size());
        if (input) g_env->SetByteArrayRegion(input, 0, (jsize)originalBytes.size(), (const jbyte*)originalBytes.data());
        if (transformOwned && transformer && owned && input && !g_env->ExceptionCheck())
            newBytesArr = (jbyteArray)g_env->CallStaticObjectMethod(bridge, transformOwned,
                transformer, targetNameObj, input, owned);
        if (input) g_env->DeleteLocalRef(input);
        if (owned) g_env->DeleteLocalRef(owned);
        if (strings) g_env->DeleteLocalRef(strings);
        if (bridge) g_env->DeleteLocalRef(bridge);
        if (!newBytesArr || g_env->ExceptionCheck()) {
            LOG("ERROR","G6","Owned-config transformation failed for %s; entrypoints will not start",
                targetNameUtf ? targetNameUtf : "<unknown>");
            if (!g_env->ExceptionCheck()) {
                jclass failure = g_env->FindClass("java/lang/IllegalStateException");
                if (failure) g_env->ThrowNew(failure, "Owned Mixin transformation unavailable");
            }
            return;
        }
        LOG("INFO","G6","transform result bytes=%d", newBytesArr ? (int)g_env->GetArrayLength(newBytesArr) : 0);

        bool unchanged = SameCapturedBytes(g_env, originalBytes, newBytesArr);
        if (g_env->ExceptionCheck()) return;
        if (unchanged) {
            LOG("INFO","G6","Unchanged transformed bytes; adapter/redefine not needed");
            g_env->DeleteLocalRef(newBytesArr);
            if (targetNameUtf) g_env->ReleaseStringUTFChars(targetNameObj, targetNameUtf);
            if (targetNameObj) g_env->DeleteLocalRef(targetNameObj);
            continue;
        }

        jbyteArray toRedefine = newBytesArr;
        bool ownAlloc = false;
        if (!toRedefine) {
            LOG("INFO","G6","transform returned null; target left unchanged");
            if (targetNameUtf) g_env->ReleaseStringUTFChars(targetNameObj, targetNameUtf);
            if (targetNameObj) g_env->DeleteLocalRef(targetNameObj);
            continue;
        }

        jclass adapterClass = FindClassByLoader("mod.runtime.LoadedClassAdapter");
        jmethodID adapt = adapterClass ? g_env->GetStaticMethodID(adapterClass, "adapt",
            "([B[BLjava/lang/Class;[Ljava/lang/String;)[B") : nullptr;
        jclass stringClass = g_env->FindClass("java/lang/String");
        jobjectArray packages = stringClass ? g_env->NewObjectArray((jsize)g_adapterMixinPackages.size(), stringClass, nullptr) : nullptr;
        for (jsize n = 0; packages && n < (jsize)g_adapterMixinPackages.size() && !g_env->ExceptionCheck(); ++n) {
            jstring package = g_env->NewStringUTF(g_adapterMixinPackages[n].c_str());
            g_env->SetObjectArrayElement(packages, n, package);
            if (package) g_env->DeleteLocalRef(package);
        }
        jbyteArray adapterInput = g_env->NewByteArray((jsize)originalBytes.size());
        if (adapterInput) g_env->SetByteArrayRegion(adapterInput, 0, (jsize)originalBytes.size(), (const jbyte*)originalBytes.data());
        LOG("INFO","Adapter","begin target=%s original=%zu transformed=%d packages=%zu",
            targetNameUtf ? targetNameUtf : "<unknown>", originalBytes.size(),
            g_env->GetArrayLength(newBytesArr), g_adapterMixinPackages.size());
        toRedefine = adapt && packages && adapterInput && !g_env->ExceptionCheck()
            ? (jbyteArray)g_env->CallStaticObjectMethod(adapterClass, adapt, adapterInput, newBytesArr, target, packages) : nullptr;
        if (adapterInput) g_env->DeleteLocalRef(adapterInput);
        if (packages) g_env->DeleteLocalRef(packages);
        if (stringClass) g_env->DeleteLocalRef(stringClass);
        if (adapterClass) g_env->DeleteLocalRef(adapterClass);
        if (!toRedefine || g_env->ExceptionCheck()) {
            LOG("ERROR","Adapter","failed target=%s; bootstrap cannot complete", targetNameUtf ? targetNameUtf : "<unknown>");
            if (!g_env->ExceptionCheck()) {
                jclass failure = g_env->FindClass("java/lang/IllegalStateException");
                if (failure) g_env->ThrowNew(failure, "Loaded-class adapter did not produce class bytes");
            }
            if (targetNameUtf) g_env->ReleaseStringUTFChars(targetNameObj, targetNameUtf);
            if (targetNameObj) g_env->DeleteLocalRef(targetNameObj);
            if (newBytesArr) g_env->DeleteLocalRef(newBytesArr);
            return;
        }
        ownAlloc = true;
        LOG("INFO","Adapter","complete output=%d", (int)g_env->GetArrayLength(toRedefine));

        unchanged = SameCapturedBytes(g_env, originalBytes, toRedefine);
        if (g_env->ExceptionCheck()) return;
        if (!unchanged) {
            jvmtiCapabilities caps2 = {};
            caps2.can_redefine_classes = 1; caps2.can_redefine_any_class = 1;
            jvmtiError capError = jt->AddCapabilities(&caps2);
            if (capError) { JvmtiMethodTable::ReportError(g_env, "AddCapabilities(direct redefine)", capError); return; }

            jint bl = g_env->GetArrayLength(toRedefine);
            jbyte* buf = g_env->GetByteArrayElements(toRedefine, nullptr);
            if (buf) {
                {
                    std::lock_guard<std::mutex> lk(g_ownRedefinesMutex);
                    g_ownRedefineInProgress.insert(target);
                }
                jvmtiClassDefinition def = {target, bl, (unsigned char*)buf};
                jvmtiError err = jt->RedefineClasses(1, &def);
                {
                    std::lock_guard<std::mutex> lk(g_ownRedefinesMutex);
                    g_ownRedefineInProgress.erase(target);
                }
                g_env->ReleaseByteArrayElements(toRedefine, buf, JNI_ABORT);
                if (err == JVMTI_ERROR_NONE) {
                    ++g_tc_targetsTransformed;
                    ++g_tc_redefineSucceeded;
                    LOG("INFO","G6","redefineClass0 OK for target");
                } else {
                    LOG("WARN","G6","redefineClass0 failed: err=%d", (int)err);
                    jclass failure = g_env->FindClass("java/lang/IllegalStateException");
                    if (failure) g_env->ThrowNew(failure, "JVMTI rejected adapted target; bootstrap is incomplete");
                    return;
                }
            }
        }

        if (ownAlloc && toRedefine) g_env->DeleteLocalRef(toRedefine);
        if (newBytesArr) g_env->DeleteLocalRef(newBytesArr);
        if (targetNameUtf) g_env->ReleaseStringUTFChars(targetNameObj, targetNameUtf);
        if (targetNameObj) g_env->DeleteLocalRef(targetNameObj);
    }

    if (transformMethod) g_env->DeleteLocalRef(transformMethod);
    if (transformer)     g_env->DeleteLocalRef(transformer);
    if (transformerCls)  g_env->DeleteLocalRef(transformerCls);
}

)RAWCODE";
}

void CodeGen::emitAccessWidenerBridge(std::stringstream& cpp, const CodeGenConfig& config) {
    if (config.accessWideners.empty()) {
        cpp << "static void ApplyAccessWideners() { }\n\n";
        return;
    }
    cpp << "static void ApplyAccessWideners() {\n";
    for (const auto& aw : config.accessWideners) {
        cpp << "    {\n";
        cpp << "        jclass lawCls = FindClassByLoader(\"net.fabricmc.loader.impl.FabricLoaderImpl\");\n";
        cpp << "        if (lawCls) {\n";
        cpp << "            jmethodID getInst = g_env->GetStaticMethodID(lawCls, \"INSTANCE\",\n";
        cpp << "                \"Lnet/fabricmc/loader/impl/FabricLoaderImpl;\");\n";
        cpp << "            if (g_env->ExceptionCheck()) g_env->ExceptionClear();\n";
        cpp << "            auto it = g_resources.find(\"" << aw.resourcePath << "\");\n";
        cpp << "            if (it != g_resources.end()) {\n";
        cpp << "                LOG(\"INFO\",\"AccessWidener\",\"found " << aw.resourcePath << " (%zu bytes)\", it->second.size());\n";
        cpp << "            }\n";
        cpp << "            g_env->DeleteLocalRef(lawCls);\n";
        cpp << "        } else {\n";
        cpp << "            LOG(\"WARN\",\"AccessWidener\",\"FabricLoaderImpl not found вЂ” AW will be applied by Java bridge\");\n";
        cpp << "        }\n";
        cpp << "    }\n";
    }
    cpp << "}\n\n";
}




void CodeGen::emitRefmapIntegration(std::stringstream& cpp, const CodeGenConfig& config) {
    if (config.refmaps.empty()) {
        cpp << "static void InstallRefmaps() { }\n\n";
        return;
    }
    cpp << "static void InstallRefmaps() {\n";
    for (const auto& rm : config.refmaps) {
        cpp << "    LOG(\"INFO\",\"Refmap\",\"" << rm.resourcePath << " embedded (%zu bytes)\", \n";
        cpp << "        g_resources.count(\"" << rm.resourcePath << "\") ? g_resources[\"" << rm.resourcePath << "\"].size() : 0);\n";
    }
    cpp << "    {\n";
    for (const auto& rm : config.refmaps) {
        cpp << "        if (!g_resources.count(\"" << rm.resourcePath << "\"))\n";
        cpp << "            LOG(\"ERROR\",\"Refmap\",\"MISSING in resource table: " << rm.resourcePath << "\");\n";
    }
    cpp << "    }\n";
    cpp << "}\n\n";
}




void CodeGen::emitResourceTable(std::stringstream& cpp,
                                 const CodeGenConfig& config,
                                 std::map<std::string, std::string>& rVar) {

    struct ResEntry { std::string name; const std::vector<uint8_t>* data; };
    std::vector<ResEntry> allRes;
    for (const auto& r : config.resources)
        allRes.push_back({r.name, &r.data});
    for (const auto& rm : config.refmaps)
        allRes.push_back({rm.resourcePath, &rm.rawBytes});
    for (const auto& aw : config.accessWideners)
        allRes.push_back({aw.resourcePath, &aw.rawBytes});

    if (allRes.empty()) {
        cpp << "static void InitResources() {}\n\n";
        return;
    }


    for (const auto& r : allRes) {
        if (rVar.count(r.name)) continue;
        std::string var = rndName(8, 12) + "_res";
        rVar[r.name] = var;
        cpp << "static const uint8_t " << var << "[] = {";
        for (size_t i = 0; i < r.data->size(); i++) {
            if (i % 20 == 0) cpp << "\n    ";
            cpp << (unsigned int)(uint8_t)(*r.data)[i];
            if (i < r.data->size() - 1) cpp << ",";
        }
        cpp << "\n};\n";
        cpp << "static const size_t " << var << "_sz = " << r.data->size() << ";\n\n";
    }


    cpp << "static void InitResources() {\n";
    for (const auto& r : allRes) {
        if (!rVar.count(r.name)) continue;

        std::string esc;
        for (char c : r.name) {
            if (c == '\\') esc += "\\\\";
            else esc += c;
        }
        auto& v = rVar[r.name];
        cpp << "    g_resources[\"" << esc << "\"] = std::vector<uint8_t>("
            << v << ", " << v << " + " << v << "_sz);\n";

        cpp << "    g_resources[\"/\" + std::string(\"" << esc << "\")] = g_resources[\"" << esc << "\"];\n";
    }
    cpp << "}\n\n";
}




void CodeGen::emitBootstrapSequence(std::stringstream& cpp,
                                     const std::vector<JarEntry>& classes,
                                     const std::vector<std::pair<std::string,std::string>>& entryClasses,
                                     const CodeGenConfig& config,
                                     const std::map<std::string, std::string>& cVar) {
    cpp << "static DWORD WINAPI MainThread(LPVOID) {\n";
    cpp << "    { char p[MAX_PATH]; DWORD n = GetTempPathA(MAX_PATH, p); if (n && n < MAX_PATH) { strcat(p, \"mod_payload_entered.txt\"); FILE* f = fopen(p, \"w\"); if (f) { fputs(\"entered MainThread\\n\", f); fclose(f); } } }\n";
    cpp << "    InitLog();\n";
    cpp << "    LOG(\"INFO\",\"Boot\",\"Step 1: attach JVM\");\n";
    cpp << "    if (!AttachJVM()) { CloseLog(); return 1; }\n\n";




    cpp << "    LOG(\"INFO\",\"Boot\",\"Step 2: acquire JVMTI\");\n";
    cpp << "    jvmtiEnv* jvmti_init = nullptr;\n";
    cpp << "    g_jvm->GetEnv((void**)&jvmti_init, JVMTI_VERSION_1_2);\n";
    cpp << "    g_jvmti = jvmti_init;\n";
    cpp << "    if (!jvmti_init) { LOG(\"ERROR\",\"Boot\",\"JVMTI unavailable\"); DetachJVM(); CloseLog(); return 1; }\n\n";
    std::set<std::string> methodPrefixes;
    for (const auto& sourceClass : classes) {
        std::string internal = sourceClass.name;
        if (internal.size() > 6 && internal.substr(internal.size() - 6) == ".class")
            internal.resize(internal.size() - 6);
        std::replace(internal.begin(), internal.end(), '.', '/');
        const auto firstSlash = internal.find('/');
        if (firstSlash == std::string::npos) {
            methodPrefixes.insert("L" + internal + ";");
            continue;
        }



        const std::string root = internal.substr(0, firstSlash);
        const bool generic = root == "com" || root == "org" || root == "net" ||
                             root == "io" || root == "dev" || root == "edu" || root == "gov";
        size_t end = firstSlash;
        if (generic) {
            for (int component = 0; component < 2; ++component) {
                const auto next = internal.find('/', end + 1);
                if (next == std::string::npos) break;
                end = next;
            }
        }
        methodPrefixes.insert("L" + internal.substr(0, end + 1));
    }
    cpp << "    if (!JvmtiMethodTable::ConfigureMethodHiding({";
    bool firstPrefix = true;
    for (const auto& prefix : methodPrefixes) {
        if (!firstPrefix) cpp << ", ";
        cpp << "\"" << prefix << "\"";
        firstPrefix = false;
    }
    cpp << "})) { LOG(\"ERROR\",\"Boot\",\"Method-hiding package configuration failed\"); DetachJVM(); CloseLog(); return 1; }\n";
    cpp << "    jvmtiError tableError = JvmtiMethodTable::Install(g_jvm, jvmti_init);\n";
    cpp << "    if (tableError) { LOG(\"ERROR\",\"Boot\",\"JVMTI table install failed err=%d\", tableError); jvmti_init->DisposeEnvironment(); g_jvmti = nullptr; DetachJVM(); CloseLog(); return 1; }\n";
    cpp << "    struct TableTransaction { bool committed = false; ~TableTransaction() { if (!committed) JvmtiMethodTable::Restore(); } } tableTransaction;\n";


    cpp << "    LOG(\"INFO\",\"Boot\",\"Step 3: ClassFileLoadHook - G4 cache for post-inject class loads\");\n";
    cpp << "    {\n";
    cpp << "        jvmtiCapabilities caps_hook = {};\n";
        cpp << "        caps_hook.can_retransform_classes = 1;\n";
        cpp << "        jvmtiError capErr = jvmti_init->AddCapabilities(&caps_hook);\n";
        cpp << "        LOG(\"INFO\",\"JVMTI\",\"AddCapabilities(retransform_classes) err=%d\", (int)capErr);\n";
        cpp << "        caps_hook = {};\n";
        cpp << "        caps_hook.can_retransform_any_class = 1;\n";
        cpp << "        capErr = jvmti_init->AddCapabilities(&caps_hook);\n";
        cpp << "        LOG(\"INFO\",\"JVMTI\",\"AddCapabilities(retransform_any_class) err=%d\", (int)capErr);\n";
        cpp << "        caps_hook = {};\n";
        cpp << "        caps_hook.can_redefine_classes = 1;\n";
        cpp << "        capErr = jvmti_init->AddCapabilities(&caps_hook);\n";
        cpp << "        LOG(\"INFO\",\"JVMTI\",\"AddCapabilities(redefine_classes) err=%d\", (int)capErr);\n";
        cpp << "        caps_hook = {};\n";
        cpp << "        caps_hook.can_redefine_any_class = 1;\n";
        cpp << "        capErr = jvmti_init->AddCapabilities(&caps_hook);\n";
        cpp << "        LOG(\"INFO\",\"JVMTI\",\"AddCapabilities(redefine_any_class) err=%d\", (int)capErr);\n";
    cpp << "        caps_hook = {};\n";
    cpp << "        caps_hook.can_get_bytecodes = 1;\n";
    cpp << "        capErr = jvmti_init->AddCapabilities(&caps_hook);\n";
    cpp << "        LOG(\"INFO\",\"JVMTI\",\"AddCapabilities(get_bytecodes) err=%d\", (int)capErr);\n";
    cpp << "        jvmtiCapabilities haveCaps = {};\n";
    cpp << "        jvmtiError getCapErr = jvmti_init->GetCapabilities(&haveCaps);\n";
    cpp << "        LOG(\"INFO\",\"JVMTI\",\"GetCapabilities err=%d retransform=%d retransform_any=%d redefine=%d redefine_any=%d bytecodes=%d\",\n";
        cpp << "            (int)getCapErr, (int)haveCaps.can_retransform_classes,\n";
    cpp << "            (int)haveCaps.can_retransform_any_class, (int)haveCaps.can_redefine_classes,\n";
    cpp << "            (int)haveCaps.can_redefine_any_class, (int)haveCaps.can_get_bytecodes);\n";
    cpp << "        static jvmtiEventCallbacks combined_cb = {};\n";
    cpp << "        combined_cb.VMDeath = [](jvmtiEnv*, JNIEnv*) { JvmtiMethodTable::Restore(); };\n";
    cpp << "        combined_cb.ClassFileLoadHook = [](jvmtiEnv* jt, JNIEnv*,\n";
    cpp << "                jclass cr, jobject, const char*,\n";
    cpp << "                jobject, jint dl, const unsigned char* d,\n";
    cpp << "                jint* ndl, unsigned char** nd) {\n";
    cpp << "            if (!d || dl == 0) return;\n";
    cpp << "            ++g_tc_classesObserved;\n";
    cpp << "            if (cr) {\n";
    cpp << "                std::lock_guard<std::mutex> lk(g_ownRedefinesMutex);\n";
    cpp << "                if (g_ownRedefineInProgress.count(cr)) return;\n";
    cpp << "            }\n";
    cpp << "            if (cr) {\n";
    cpp << "                std::lock_guard<std::mutex> lk(g_classBytesMutex);\n";
    cpp << "                if (!g_originalClassBytes.count(cr)) {\n";
    cpp << "                    g_originalClassBytes[cr] = std::vector<uint8_t>(d, d + dl);\n";
    cpp << "                    ++g_tc_originalBytesCached;\n";
    cpp << "                }\n";
    cpp << "            }\n";
    cpp << "            if (cr && g_protectedClasses.count(cr)) {\n";
    cpp << "                if (jt->Allocate(dl, nd) == JVMTI_ERROR_NONE) {\n";
    cpp << "                    memcpy(*nd, d, dl);\n";
    cpp << "                    *ndl = dl;\n";
    cpp << "                }\n";
    cpp << "                ++g_tc_protectedClasses;\n";
    cpp << "                return;\n";
    cpp << "            }\n";
    cpp << "        };\n";
    cpp << "        jvmtiError callbackError = jvmti_init->SetEventCallbacks(&combined_cb, sizeof(combined_cb));\n";
    cpp << "        if (!callbackError) callbackError = jvmti_init->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, nullptr);\n";
    cpp << "        if (!callbackError) callbackError = jvmti_init->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);\n";
    cpp << "        if (callbackError) { LOG(\"ERROR\",\"Boot\",\"JVMTI callback installation failed err=%d\", callbackError); return 1; }\n";
    cpp << "        g_primaryCallbacks = combined_cb; g_primaryClassHookEnabled = true;\n";
    cpp << "    }\n\n";
    cpp << "    LOG(\"INFO\",\"Boot\",\"Step 4: find KnotClassLoader\");\n";
    cpp << "    if (!FindClassLoader()) { LOG(\"ERROR\",\"Boot\",\"ClassLoader not found вЂ” FATAL\"); DetachJVM(); CloseLog(); return 1; }\n\n";

    cpp << "    LOG(\"INFO\",\"Boot\",\"Step 5: init resource table\");\n";
    cpp << "    InitResources();\n\n";


    for (const auto& c : classes) {
        if (!cVar.count(c.name)) continue;
        const auto& var = cVar.at(c.name);
        std::string resourceName = c.name;
        std::replace(resourceName.begin(), resourceName.end(), '.', '/');
        resourceName += ".class";
        cpp << "    g_resources[\"" << resourceName << "\"] = std::vector<uint8_t>((const uint8_t*)"
            << var << ", (const uint8_t*)" << var << " + " << var << "_sz);\n";
    }
    cpp << "    LOG(\"INFO\",\"Resources\",\"Published %zu embedded class resources\", (size_t)" << classes.size() << "ULL);\n";

    cpp << "    LOG(\"INFO\",\"Boot\",\"Step 6: load embedded runtime classes (G8)\");\n";
    cpp << "    LoadRuntimeClasses();\n\n";
    cpp << "    LOG(\"INFO\",\"Boot\",\"Step 6b: load %zu mod classes\", (size_t)" << classes.size() << "ULL);\n";
    for (const auto& c : classes) {
        std::string cname = c.name;
        if (cname.size() > 6 && cname.substr(cname.size() - 6) == ".class")
            cname = cname.substr(0, cname.size() - 6);
        std::replace(cname.begin(), cname.end(), '.', '/');
        if (cVar.count(c.name)) {
            cpp << "    LoadClass(\"" << cname << "\", " << cVar.at(c.name)
                << ", " << cVar.at(c.name) << "_sz);\n";
        }
    }
    cpp << "\n";

    cpp << "    LOG(\"INFO\",\"Boot\",\"Step 7: register native bridge (G1+G7)\");\n";
    cpp << "    RegisterNativeBridge();\n\n";

    cpp << "    LOG(\"INFO\",\"Boot\",\"Step 8: install MixinService wrapper (G1+G8+G13)\");\n";
    cpp << "    InstallMixinServiceWrapper();\n\n";

    cpp << "    LOG(\"INFO\",\"Boot\",\"Step 9: apply AccessWideners (G12)\");\n";
    cpp << "    ApplyAccessWideners();\n\n";

    cpp << "    LOG(\"INFO\",\"Boot\",\"Step 10: install refmaps (G11)\");\n";
    cpp << "    InstallRefmaps();\n\n";

    cpp << "    LOG(\"INFO\",\"Boot\",\"Step 11: inject mixin configs transactionally (G3)\");\n";
    cpp << "    InjectMixinConfigsInternal();\n\n";
    cpp << "    if (!g_internalConfigInjected || g_env->ExceptionCheck()) {\n";
    cpp << "        LOG(\"ERROR\",\"Boot\",\"Config preparation failed; capture and entrypoints not started\");\n";
    cpp << "        if (g_env->ExceptionCheck()) g_env->ExceptionDescribe();\n";
    cpp << "        return 1;\n";
    cpp << "    }\n";

    cpp << R"CPP(    LOG("INFO","Boot","Step 12: prepared Mixin metadata retained until transform completes");

)CPP";

    cpp << "    LOG(\"INFO\",\"Boot\",\"Step 13: direct transform/redefine loaded targets (G6)\");\n";
    cpp << "    DirectTransformAndRedefineTargets();\n\n";
    cpp << "    if (g_env->ExceptionCheck()) {\n";
    cpp << "        LOG(\"ERROR\",\"Boot\",\"Mixin target processing failed; entrypoints not started\");\n";
    cpp << "        g_env->ExceptionDescribe(); g_env->ExceptionClear();\n";
    cpp << "        DetachJVM(); CloseLog(); return 1;\n";
    cpp << "    }\n";

    cpp << R"CPP(    LOG("INFO","Boot","Step 14: invalidate ClassInfo metadata after transform");
    {
        jclass bridge = FindClassByLoader("mod.runtime.NativeBridge");
        jmethodID clear = bridge ? g_env->GetStaticMethodID(bridge, "clearMixinCache0", "()V") : nullptr;
        if (clear) g_env->CallStaticVoidMethod(bridge, clear);
        bool failed = !clear || g_env->ExceptionCheck();
        if (bridge) g_env->DeleteLocalRef(bridge);
        if (failed) {
            LOG("ERROR","Boot","Post-transform cache invalidation failed; entrypoints not started");
            if (g_env->ExceptionCheck()) g_env->ExceptionDescribe();
            return 1;
        }
    }

)CPP";


    cpp << "    LOG(\"INFO\",\"Boot\",\"Step 15: run Fabric entrypoints (G9: AFTER mixin setup)\");\n";
    for (const auto& [cname, type] : entryClasses) {
        cpp << "    {\n";
        cpp << "        jclass cls = g_classes.count(\"" << cname << "\") ? g_classes[\"" << cname << "\"] : FindClassByLoader(\"" << cname << "\");\n";
        cpp << "        if (!cls) {\n";
        cpp << "            LogPendingEntrypointException(\"class lookup\", \"" << cname << "\");\n";
        cpp << "            LOG(\"ERROR\",\"Entrypoint\",\"Class not found: " << cname << "\");\n";
        cpp << "            return 1;\n";
        cpp << "        }\n";
        cpp << "        if (!InitMod(cls, \"" << cname << "\", \"" << type << "\")) {\n";
        cpp << "            LOG(\"ERROR\",\"Boot\",\"Entrypoint failed: " << cname << "\");\n";
        cpp << "            return 1;\n";
        cpp << "        }\n";
        cpp << "    }\n";
    }

    cpp << "\n    LOG(\"INFO\",\"Boot\",\"Bootstrap complete.\");\n";
    cpp << "    PrintTraceCounters();\n";
    cpp << "    tableTransaction.committed = true;\n";
    cpp << "    return 0;\n";
    cpp << "}\n\n";
}




std::string CodeGen::generate(const std::vector<JarEntry>& classes,
                               const std::string& entryPoint,
                               const std::string& platform,
                               const CodeGenConfig& config) {
    std::stringstream cpp;
    std::map<std::string, std::string> cVar;
    std::map<std::string, std::string> rVar;
    std::map<std::string, std::string> rtVar;

    for (const auto& c : classes) cVar[c.name] = rndName(8, 12);
    uint32_t uid = static_cast<uint32_t>(rng()) & 0xFFFFFF;

    cpp << "#define _CRT_SECURE_NO_WARNINGS\n";
    cpp << "#include <windows.h>\n#include <jni.h>\n#include <jvmti.h>\n";
    cpp << "#include <stdio.h>\n#include <string.h>\n#include <stdarg.h>\n";
    cpp << "#include <string>\n#include <map>\n#include <set>\n#include <vector>\n#include <algorithm>\n";
    cpp << "#include <mutex>\n#include <atomic>\n#include <unordered_map>\n#include <tlhelp32.h>\n\n";

    cpp << "static JavaVM* g_jvm = nullptr;\n";
    cpp << "static jvmtiEnv* g_jvmti = nullptr;\n";
    cpp << "static JNIEnv* g_env = nullptr;\n";
    cpp << "static jobject g_loader = nullptr;\n";
    cpp << "static jmethodID g_loadClassMethod = nullptr;\n";
    cpp << "static HMODULE g_mod = nullptr;\n";
    cpp << "static DWORD g_start = 0;\n";
    cpp << "static const DWORD g_uid = 0x" << std::hex << uid << std::dec << ";\n";
    cpp << "static std::map<std::string, jclass> g_classes;\n";
    cpp << "static std::map<std::string, std::vector<uint8_t>> g_resources;\n";
    cpp << "static std::set<jclass> g_protectedClasses;\n\n";
    cpp << "static std::vector<HANDLE> g_suspendedThreads;\n";
    cpp << "static void LOG(const char* lvl, const char* tag, const char* fmt, ...);\n\n";


    emitTraceCounters(cpp);


    if (config.enableLogging) {
        cpp << "static FILE* g_log = nullptr;\n";
        cpp << "static FILE* g_runLog = nullptr;\n";
        cpp << "static std::mutex g_logMutex;\n";
        cpp << "static void LOG(const char* lvl, const char* tag, const char* fmt, ...) {\n";
        cpp << "    DWORD t = GetTickCount() - g_start;\n";
        cpp << "    char buf[2048]; va_list args; va_start(args, fmt); vsnprintf(buf, sizeof(buf), fmt, args); va_end(args);\n";
        cpp << "    char line[2200]; snprintf(line, sizeof(line), \"[%u ms] [tid=%lu] [%s] %s: %s\\n\", t, GetCurrentThreadId(), lvl, tag, buf);\n";
        cpp << "    std::lock_guard<std::mutex> logLock(g_logMutex);\n";
        cpp << "    if (g_log) { fputs(line, g_log); fflush(g_log); }\n";
        cpp << "    if (g_runLog) { fputs(line, g_runLog); fflush(g_runLog); }\n";
        cpp << "}\n";
        cpp << "static void InitLog() {\n";
        cpp << "    std::lock_guard<std::mutex> logLock(g_logMutex);\n";
        cpp << "    g_start = GetTickCount();\n";
        cpp << "    char path[MAX_PATH];\n";
        cpp << "    DWORD tmpLen = GetTempPathA(MAX_PATH, path);\n";
        cpp << "    if (tmpLen == 0 || tmpLen >= MAX_PATH) strcpy(path, \".\\\\\");\n";
        cpp << "    char runPath[MAX_PATH + 80]; snprintf(runPath, sizeof(runPath), \"%smod_payload_%lu_%06X.log\", path, GetCurrentProcessId(), g_uid);\n";
        cpp << "    g_runLog = fopen(runPath, \"a\");\n";
        cpp << "    strcat(path, \"mod_payload_log.txt\");\n";
        cpp << "    g_log = fopen(path, \"w\");\n";
        cpp << "    if (g_log) {\n";
        cpp << "        SYSTEMTIME st; GetLocalTime(&st);\n";
        cpp << "        fprintf(g_log, \"JAR-TO-DLL Log [UID: 0x%06X]\\nDate: %04d-%02d-%02d %02d:%02d:%02d\\nPID: %u\\n\\n\",\n";
        cpp << "            g_uid, st.wYear, st.wMonth, st.wDay, st.wHour, st.wMinute, st.wSecond, GetCurrentProcessId());\n";
        cpp << "        fflush(g_log);\n";
        cpp << "    }\n";
        cpp << "}\n";
        cpp << "static void CloseLog() { std::lock_guard<std::mutex> logLock(g_logMutex); if (g_log) { fprintf(g_log, \"\\nClosed after %u ms\\n\", GetTickCount() - g_start); fclose(g_log); g_log = nullptr; } if (g_runLog) { fclose(g_runLog); g_runLog = nullptr; } }\n\n";
    } else {
        cpp << "#define LOG(lvl, tag, fmt, ...) ((void)0)\n";
        cpp << "static void InitLog() { g_start = GetTickCount(); }\n";
        cpp << "static void CloseLog() {}\n\n";
    }

    cpp << R"RAWCODE(
static void SuspendManagedThreads() {
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPTHREAD, 0);
    if (snap == INVALID_HANDLE_VALUE) return;
    THREADENTRY32 te{}; te.dwSize = sizeof(te);
    DWORD self = GetCurrentThreadId(), pid = GetCurrentProcessId();
    if (Thread32First(snap, &te)) do {
        if (te.th32OwnerProcessID != pid || te.th32ThreadID == self) continue;
        HANDLE h = OpenThread(THREAD_SUSPEND_RESUME, FALSE, te.th32ThreadID);
        if (h && SuspendThread(h) != (DWORD)-1) g_suspendedThreads.push_back(h);
        else if (h) CloseHandle(h);
    } while (Thread32Next(snap, &te));
    CloseHandle(snap);
    LOG("INFO", "Runtime", "thread gate suspended %zu JVM threads", g_suspendedThreads.size());
}
static void ResumeManagedThreads() {
    for (HANDLE h : g_suspendedThreads) { ResumeThread(h); CloseHandle(h); }
    g_suspendedThreads.clear();
    LOG("INFO", "Runtime", "thread gate resumed");
}

)RAWCODE";

    cpp << jvmtiTableSource << "\n";


    emitClassByteCache(cpp);


    cpp << "static bool AttachJVM() {\n";
    cpp << "    HMODULE jvmLib = GetModuleHandleA(\"jvm.dll\");\n";
    cpp << "    if (!jvmLib) { LOG(\"ERROR\",\"JVM\",\"jvm.dll not found\"); return false; }\n";
    cpp << "    typedef jint(JNICALL* GetVMs_t)(JavaVM**, jsize, jsize*);\n";
    cpp << "    GetVMs_t GetVMs = (GetVMs_t)GetProcAddress(jvmLib, \"JNI_GetCreatedJavaVMs\");\n";
    cpp << "    if (!GetVMs) return false;\n";
    cpp << "    jsize cnt = 0;\n";
    cpp << "    if (GetVMs(&g_jvm, 1, &cnt) != JNI_OK || cnt == 0) return false;\n";
    cpp << "    jint r = g_jvm->GetEnv((void**)&g_env, JNI_VERSION_1_8);\n";
    cpp << "    if (r == JNI_EDETACHED) r = g_jvm->AttachCurrentThread((void**)&g_env, nullptr);\n";
    cpp << "    return r == JNI_OK;\n";
    cpp << "}\n\n";

    cpp << "static void DetachJVM() {\n";
    cpp << "    if (g_loader) { g_env->DeleteGlobalRef(g_loader); g_loader = nullptr; }\n";
    cpp << "    if (g_jvm) { g_jvm->DetachCurrentThread(); g_env = nullptr; }\n";
    cpp << "}\n\n";




    cpp << "static jclass FindLoadedClassNoLoad(const char* name) {\n";
    cpp << "    if (!g_jvm || !g_env || !name) return nullptr;\n";
    cpp << "    JvmtiMethodTable::Environment ownedEnvironment(g_jvm);\n";
    cpp << "    jvmtiEnv* jt = ownedEnvironment.get();\n";
    cpp << "    if (!jt) return nullptr;\n";
    cpp << "    std::string wanted(name);\n";
    cpp << "    for (char& c : wanted) if (c == '.') c = '/';\n";
    cpp << "    jint count = 0; jclass* classes = nullptr;\n";
    cpp << "    if (jt->GetLoadedClasses(&count, &classes) != JVMTI_ERROR_NONE || !classes) return nullptr;\n";
    cpp << "    jclass result = nullptr;\n";
    cpp << "    for (jint i = 0; i < count && !result; ++i) {\n";
    cpp << "        char* sig = nullptr;\n";
    cpp << "        if (jt->GetClassSignature(classes[i], &sig, nullptr) == JVMTI_ERROR_NONE && sig) {\n";
    cpp << "            std::string current(sig);\n";
    cpp << "            if (current.size() > 2 && current.front() == 'L' && current.back() == ';')\n";
    cpp << "                current = current.substr(1, current.size() - 2);\n";
    cpp << "            if (current == wanted) result = (jclass)g_env->NewLocalRef(classes[i]);\n";
    cpp << "            jt->Deallocate((unsigned char*)sig);\n";
    cpp << "        }\n";
    cpp << "    }\n";
    cpp << "    for (jint i = 0; i < count; ++i) g_env->DeleteLocalRef(classes[i]);\n";
    cpp << "    jt->Deallocate((unsigned char*)classes);\n";
    cpp << "    return result;\n";
    cpp << "}\n\n";


    cpp << "static jclass FindClassByLoader(const char* name) {\n";
    cpp << "    if (!g_env || !g_loader || !g_loadClassMethod) return nullptr;\n";
    cpp << "    std::string className(name);\n";
    cpp << "    for (char& c : className) { if (c == '/') c = '.'; }\n";
    cpp << "    jstring jName = g_env->NewStringUTF(className.c_str());\n";
    cpp << "    if (!jName) return nullptr;\n";
    cpp << "    jclass cls = (jclass)g_env->CallObjectMethod(g_loader, g_loadClassMethod, jName);\n";
    cpp << "    g_env->DeleteLocalRef(jName);\n";
    cpp << "    if (g_env->ExceptionCheck()) { g_env->ExceptionClear(); return nullptr; }\n";
    cpp << "    return cls;\n";
    cpp << "}\n\n";


    cpp << "static bool FindClassLoader() {\n";
    cpp << "    jclass threadClass = g_env->FindClass(\"java/lang/Thread\");\n";
    cpp << "    jclass mapClass = g_env->FindClass(\"java/util/Map\");\n";
    cpp << "    jclass setClass = g_env->FindClass(\"java/util/Set\");\n";
    cpp << "    if (!threadClass||!mapClass||!setClass) return false;\n";
    cpp << "    jmethodID getAllStackTraces = g_env->GetStaticMethodID(threadClass,\"getAllStackTraces\",\"()Ljava/util/Map;\");\n";
    cpp << "    jmethodID keySet = g_env->GetMethodID(mapClass,\"keySet\",\"()Ljava/util/Set;\");\n";
    cpp << "    jmethodID toArray = g_env->GetMethodID(setClass,\"toArray\",\"()[Ljava/lang/Object;\");\n";
    cpp << "    jmethodID getCCL = g_env->GetMethodID(threadClass,\"getContextClassLoader\",\"()Ljava/lang/ClassLoader;\");\n";
    cpp << "    jmethodID getName = g_env->GetMethodID(threadClass,\"getName\",\"()Ljava/lang/String;\");\n";
    cpp << "    if (!getAllStackTraces||!keySet||!toArray||!getCCL||!getName) return false;\n";
    cpp << "    jobject stackMap = g_env->CallStaticObjectMethod(threadClass, getAllStackTraces);\n";
    cpp << "    jobject threadSet = g_env->CallObjectMethod(stackMap, keySet);\n";
    cpp << "    jobjectArray threads = (jobjectArray)g_env->CallObjectMethod(threadSet, toArray);\n";
    cpp << "    jint count = g_env->GetArrayLength(threads);\n";
    cpp << "    for (jint i = 0; i < count && !g_loader; i++) {\n";
    cpp << "        jobject thread = g_env->GetObjectArrayElement(threads, i);\n";
    cpp << "        jobject cl = g_env->CallObjectMethod(thread, getCCL);\n";
    cpp << "        if (cl) {\n";
    cpp << "            jclass clCls = g_env->GetObjectClass(cl);\n";
    cpp << "            jclass classClass = g_env->FindClass(\"java/lang/Class\");\n";
    cpp << "            jmethodID getNameM = g_env->GetMethodID(classClass,\"getName\",\"()Ljava/lang/String;\");\n";
    cpp << "            jstring loaderName = (jstring)g_env->CallObjectMethod(clCls, getNameM);\n";
    cpp << "            const char* lnc = g_env->GetStringUTFChars(loaderName, nullptr);\n";
    cpp << "            std::string lns(lnc);\n";
    cpp << "            g_env->ReleaseStringUTFChars(loaderName, lnc);\n";
    cpp << "            g_env->DeleteLocalRef(loaderName); g_env->DeleteLocalRef(clCls);\n";
    cpp << "            if (lns.find(\"KnotClassLoader\") != std::string::npos ||\n";
    cpp << "                lns.find(\"LaunchClassLoader\") != std::string::npos) {\n";
    cpp << "                g_loader = g_env->NewGlobalRef(cl);\n";
    cpp << "            }\n";
    cpp << "            g_env->DeleteLocalRef(cl);\n";
    cpp << "        }\n";
    cpp << "        g_env->DeleteLocalRef(thread);\n";
    cpp << "    }\n";
    cpp << "    if (!g_loader) {\n";
    cpp << "        jmethodID ct = g_env->GetStaticMethodID(threadClass,\"currentThread\",\"()Ljava/lang/Thread;\");\n";
    cpp << "        jobject t = g_env->CallStaticObjectMethod(threadClass, ct);\n";
    cpp << "        if (t) { jobject cl = g_env->CallObjectMethod(t, getCCL);\n";
    cpp << "                 if (cl) g_loader = g_env->NewGlobalRef(cl);\n";
    cpp << "                 g_env->DeleteLocalRef(t); }\n";
    cpp << "    }\n";
    cpp << "    if (!g_loader) return false;\n";
    cpp << "    jclass loaderClass = g_env->GetObjectClass(g_loader);\n";
    cpp << "    g_loadClassMethod = g_env->GetMethodID(loaderClass,\"loadClass\",\"(Ljava/lang/String;)Ljava/lang/Class;\");\n";
    cpp << "    g_env->DeleteLocalRef(threads); g_env->DeleteLocalRef(threadSet); g_env->DeleteLocalRef(stackMap);\n";
    cpp << "    return g_loadClassMethod != nullptr;\n";
    cpp << "}\n\n";


    cpp << "static bool LoadClass(const char* name, jbyte* data, jsize size) {\n";
    cpp << "    jbyteArray arr = g_env->NewByteArray(size);\n";
    cpp << "    g_env->SetByteArrayRegion(arr, 0, size, data);\n";
    cpp << "    jclass loaderClass = g_env->GetObjectClass(g_loader);\n";
    cpp << "    jmethodID defineClass = g_env->GetMethodID(loaderClass,\"defineClass\",\"(Ljava/lang/String;[BII)Ljava/lang/Class;\");\n";
    cpp << "    if (!defineClass) {\n";
    cpp << "        jclass unsafeClass = g_env->FindClass(\"sun/misc/Unsafe\");\n";
    cpp << "        if (!unsafeClass) unsafeClass = g_env->FindClass(\"jdk/internal/misc/Unsafe\");\n";
    cpp << "        if (unsafeClass) {\n";
    cpp << "            jfieldID theUnsafe = g_env->GetStaticFieldID(unsafeClass,\"theUnsafe\",\"Lsun/misc/Unsafe;\");\n";
    cpp << "            if (!theUnsafe) theUnsafe = g_env->GetStaticFieldID(unsafeClass,\"theUnsafe\",\"Ljdk/internal/misc/Unsafe;\");\n";
    cpp << "            if (theUnsafe) {\n";
    cpp << "                jobject unsafe = g_env->GetStaticObjectField(unsafeClass, theUnsafe);\n";
    cpp << "                defineClass = g_env->GetMethodID(unsafeClass,\"defineClass\",\n";
    cpp << "                    \"(Ljava/lang/String;[BIILjava/lang/ClassLoader;Ljava/security/ProtectionDomain;)Ljava/lang/Class;\");\n";
    cpp << "                if (unsafe && defineClass) {\n";
    cpp << "                    std::string cn(name); for (char& c : cn) if (c=='/') c='.';\n";
    cpp << "                    jstring jn = g_env->NewStringUTF(cn.c_str());\n";
    cpp << "                    jclass cls = (jclass)g_env->CallObjectMethod(unsafe,defineClass,jn,arr,0,size,g_loader,nullptr);\n";
    cpp << "                    g_env->DeleteLocalRef(jn); g_env->DeleteLocalRef(arr);\n";
    cpp << "                    if (g_env->ExceptionCheck()) { g_env->ExceptionClear(); return false; }\n";
    cpp << "                    if (cls) { g_classes[name]=(jclass)g_env->NewGlobalRef(cls); jvmtiError tagError = JvmtiMethodTable::Protect(cls); g_env->DeleteLocalRef(cls); return tagError == JVMTI_ERROR_NONE; }\n";
    cpp << "                }\n";
    cpp << "            }\n";
    cpp << "        }\n";
    cpp << "        g_env->DeleteLocalRef(arr); return false;\n";
    cpp << "    }\n";
    cpp << "    std::string cn(name); for (char& c : cn) if (c=='/') c='.';\n";
    cpp << "    jstring jn = g_env->NewStringUTF(cn.c_str());\n";
    cpp << "    jclass cls = (jclass)g_env->CallObjectMethod(g_loader,defineClass,jn,arr,0,size);\n";
    cpp << "    g_env->DeleteLocalRef(jn); g_env->DeleteLocalRef(arr);\n";
    cpp << "    if (g_env->ExceptionCheck()) { g_env->ExceptionClear(); return false; }\n";
    cpp << "    if (cls) { g_classes[name]=(jclass)g_env->NewGlobalRef(cls); jvmtiError tagError = JvmtiMethodTable::Protect(cls); g_env->DeleteLocalRef(cls); return tagError == JVMTI_ERROR_NONE; }\n";
    cpp << "    return false;\n";
    cpp << "}\n\n";



    cpp << "static bool LogPendingEntrypointException(const char* phase, const char* name) {\n";
    cpp << "    if (!g_env->ExceptionCheck()) return false;\n";
    cpp << "    LOG(\"ERROR\",\"Entrypoint\",\"Java exception at %s for %s; full stack follows on Minecraft stderr\", phase, name);\n";
    cpp << "    g_env->ExceptionDescribe();\n";
    cpp << "    g_env->ExceptionClear();\n";
    cpp << "    return true;\n";
    cpp << "}\n\n";


    cpp << "static bool InitMod(jclass cls, const char* name, const char* type) {\n";
    cpp << "    LOG(\"INFO\",\"Entrypoint\",\"Begin class=%s type=%s jclass=%p\", name, type, (void*)cls);\n";
    cpp << "    jmethodID ctor = g_env->GetMethodID(cls,\"<init>\",\"()V\");\n";
    cpp << "    if (LogPendingEntrypointException(\"constructor lookup\", name)) return false;\n";
    cpp << "    if (!ctor) { LOG(\"ERROR\",\"Entrypoint\",\"Missing public constructor ()V class=%s\", name); return false; }\n";
    cpp << "    LOG(\"INFO\",\"Entrypoint\",\"Constructor resolved class=%s method=%p\", name, (void*)ctor);\n";
    cpp << "    jobject obj = g_env->NewObject(cls, ctor);\n";
    cpp << "    if (LogPendingEntrypointException(\"constructor execution\", name)) return false;\n";
    cpp << "    if (!obj) { LOG(\"ERROR\",\"Entrypoint\",\"Constructor returned null class=%s\", name); return false; }\n";
    cpp << "    LOG(\"INFO\",\"Entrypoint\",\"Instance created class=%s object=%p\", name, (void*)obj);\n";
    cpp << "    const char* method = (strcmp(type,\"client\")==0) ? \"onInitializeClient\"\n";
    cpp << "                       : (strcmp(type,\"server\")==0) ? \"onInitializeServer\"\n";
    cpp << "                       : \"onInitialize\";\n";
    cpp << "    jmethodID m = g_env->GetMethodID(cls, method, \"()V\");\n";
    cpp << "    if (LogPendingEntrypointException(\"entrypoint method lookup\", name)) { g_env->DeleteLocalRef(obj); return false; }\n";
    cpp << "    if (!m) { LOG(\"ERROR\",\"Entrypoint\",\"Missing method %s()V class=%s\", method, name); g_env->DeleteLocalRef(obj); return false; }\n";
    cpp << "    LOG(\"INFO\",\"Entrypoint\",\"Calling %s()V class=%s method=%p\", method, name, (void*)m);\n";
    cpp << "    g_env->CallVoidMethod(obj, m);\n";
    cpp << "    bool failed = LogPendingEntrypointException(\"entrypoint execution\", name);\n";
    cpp << "    g_env->DeleteLocalRef(obj);\n";
    cpp << "    if (failed) return false;\n";
    cpp << "    LOG(\"INFO\",\"Entrypoint\",\"Completed %s()V class=%s\", method, name);\n";
    cpp << "    return true;\n";
    cpp << "}\n\n";


    for (const auto& c : classes) {
        std::string var = cVar[c.name];
        cpp << "static jbyte " << var << "[] = {";
        for (size_t i = 0; i < c.data.size(); i++) {
            if (i % 20 == 0) cpp << "\n    ";
            cpp << (int)(int8_t)c.data[i];
            if (i < c.data.size() - 1) cpp << ",";
        }
        cpp << "\n};\n";
        cpp << "static const jsize " << var << "_sz = " << c.data.size() << ";\n\n";
    }


    emitRuntimeClassArrays(cpp, config, rtVar);


    emitResourceTable(cpp, config, rVar);


    emitNativeBridge(cpp);


    emitMixinServiceWrapper(cpp);


    emitActiveMixinConnector(cpp, config);


    emitAccessWidenerBridge(cpp, config);


    emitRefmapIntegration(cpp, config);


    std::vector<std::pair<std::string, std::string>> entryClasses;
    for (const auto& ep : config.fabricEntrypoints) {
        std::string cname = ep.className;
        std::replace(cname.begin(), cname.end(), '.', '/');
        entryClasses.push_back({cname, ep.type});
    }


    emitBootstrapSequence(cpp, classes, entryClasses, config, cVar);


    if (config.enableUnload) {
        cpp << "static volatile bool g_unloading = false;\n\n";
        cpp << "static DWORD WINAPI UnhookThread(LPVOID) {\n";
        cpp << "    while (!g_unloading) {\n";
        cpp << "        if (GetAsyncKeyState(VK_DELETE) & 0x8000) {\n";
        cpp << "            g_unloading = true;\n";
        cpp << "            CloseLog();\n";
        cpp << "            Sleep(100);\n";
        cpp << "            FreeLibraryAndExitThread(g_mod, 0);\n";
        cpp << "        }\n";
        cpp << "        Sleep(50);\n";
        cpp << "    }\n";
        cpp << "    return 0;\n";
        cpp << "}\n\n";
    }


    cpp << "extern \"C\" __declspec(dllexport) DWORD WINAPI ManualMain(HINSTANCE hDll, DWORD reason, LPVOID) {\n";
    cpp << "    g_mod = hDll;\n";
    cpp << "    return MainThread(nullptr);\n";
    cpp << "}\n\n";

    cpp << "BOOL APIENTRY DllMain(HMODULE hMod, DWORD reason, LPVOID) {\n";
    cpp << "    (void)hMod; (void)reason;\n";
    cpp << "    return TRUE;\n";
    cpp << "}\n";

    return cpp.str();
}
