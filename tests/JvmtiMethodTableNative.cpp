#include "../build/tests/jvmti-table/mod_payload.cpp"

static void JNICALL TestVmDeath(jvmtiEnv*, JNIEnv*) { JvmtiMethodTable::Restore(); }
static std::atomic<int> baselineHooks{0};
extern "C" JNIEXPORT void JNICALL Java_mod_runtime_JvmtiMethodTableRegression_lookupWithGc(JNIEnv* env, jclass test) {
    g_env = env;
    auto create = env->GetStaticMethodID(test, "createLookupFixture", "()V");
    auto release = env->GetStaticMethodID(test, "releaseLookupFixture", "()V");
    auto released = env->GetStaticMethodID(test, "lookupFixtureReleased", "()Z");
    if (env->ExceptionCheck()) return;
    env->CallStaticVoidMethod(test, create);
    if (env->ExceptionCheck()) return;
    for (int i = 0; i < 128; ++i) {
        jclass found = FindLoadedClassNoLoad("java.lang.String");
        if (env->ExceptionCheck()) return;
        if (!found) {
            JvmtiMethodTable::ReportError(env, "String lookup", JVMTI_ERROR_NOT_FOUND);
            return;
        }
        env->DeleteLocalRef(found);
        if (FindLoadedClassNoLoad("missing.LookupFixture")) {
            JvmtiMethodTable::ReportError(env, "Absent class unexpectedly found", JVMTI_ERROR_INTERNAL);
            return;
        }
        if (env->ExceptionCheck()) return;
        auto error = g_jvmti->ForceGarbageCollection();
        if (error) { JvmtiMethodTable::ReportError(env, "ForceGarbageCollection", error); return; }
    }
    env->CallStaticVoidMethod(test, release);
    if (env->ExceptionCheck()) return;
    for (int i = 0; i < 10; ++i) {
        auto error = g_jvmti->ForceGarbageCollection();
        if (error) { JvmtiMethodTable::ReportError(env, "ForceGarbageCollection", error); return; }
        bool gone = env->CallStaticBooleanMethod(test, released);
        if (env->ExceptionCheck() || gone) return;
    }
    JvmtiMethodTable::ReportError(env, "Lookup retained a disposable class loader", JVMTI_ERROR_INTERNAL);
}
extern "C" JNIEXPORT void JNICALL Java_mod_runtime_LateMixinSequenceRegression_resource(JNIEnv* env, jclass,
        jstring name, jbyteArray data) {
    const char* text = env->GetStringUTFChars(name, nullptr);
    if (!text) return;
    auto& bytes = g_resources[text];
    env->ReleaseStringUTFChars(name, text);
    bytes.resize(env->GetArrayLength(data));
    env->GetByteArrayRegion(data, 0, (jsize)bytes.size(), reinterpret_cast<jbyte*>(bytes.data()));
}
static void JNICALL TestClassHook(jvmtiEnv*, JNIEnv*, jclass cls, jobject, const char*,
        jobject, jint, const unsigned char*, jint*, unsigned char**) {
    if (cls) ++baselineHooks;
}

extern "C" JNIEXPORT jint JNICALL Java_mod_runtime_JvmtiMethodTableRegression_start(JNIEnv* env, jclass) {
    env->GetJavaVM(&g_jvm);
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&g_jvmti), JVMTI_VERSION_1_2) != JNI_OK) return -1;
    InitLog();
    if (!JvmtiMethodTable::ConfigureMethodHiding({"Lfixtures/"})) return -1;
    auto err = JvmtiMethodTable::Install(g_jvm, g_jvmti);
    if (err) return err;
    jvmtiCapabilities caps{}; caps.can_retransform_classes = 1;
    err = g_jvmti->AddCapabilities(&caps);
    if (err) return err;
    jvmtiEventCallbacks callbacks{}; callbacks.VMDeath = &TestVmDeath;
    callbacks.ClassFileLoadHook = &TestClassHook;
    g_primaryCallbacks = callbacks;
    g_primaryClassHookEnabled = true;
    err = g_jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    if (!err) err = g_jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, nullptr);
    if (!err) err = g_jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
    return err;
}

extern "C" JNIEXPORT jint JNICALL Java_mod_runtime_JvmtiMethodTableRegression_methodCount(JNIEnv*, jclass,
        jclass target, jboolean savedOriginal) {
    jint count = -1;
    jmethodID* methods = nullptr;
    auto err = savedOriginal
        ? JvmtiMethodTable::original.GetClassMethods(g_jvmti, target, &count, &methods)
        : g_jvmti->GetClassMethods(target, &count, &methods);
    if (methods) JvmtiMethodTable::original.Deallocate(g_jvmti, reinterpret_cast<unsigned char*>(methods));
    return err ? -static_cast<jint>(err) : count;
}

extern "C" JNIEXPORT jint JNICALL Java_mod_runtime_JvmtiMethodTableRegression_stop(JNIEnv*, jclass) {
    if (!JvmtiMethodTable::Restore()) return -1;
    auto err = g_jvmti->DisposeEnvironment();
    g_jvmti = nullptr;
    if (g_loader) { g_env->DeleteGlobalRef(g_loader); g_loader = nullptr; }
    CloseLog();
    return err;
}

extern "C" JNIEXPORT void JNICALL Java_mod_runtime_JvmtiMethodTableRegression_registerBridge(JNIEnv* env, jclass testClass) {
    g_env = env;
    jclass classClass = env->FindClass("java/lang/Class");
    auto getLoader = env->GetMethodID(classClass, "getClassLoader", "()Ljava/lang/ClassLoader;");
    jobject loader = env->CallObjectMethod(testClass, getLoader);
    if (env->ExceptionCheck() || !loader) return;
    g_loader = env->NewGlobalRef(loader);
    jclass loaderClass = env->GetObjectClass(loader);
    g_loadClassMethod = env->GetMethodID(loaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    RegisterNativeBridge();
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_mod_runtime_JvmtiMethodTableRegression_capture(JNIEnv* env, jclass, jclass target) {
    auto bytes = GetOriginalBytes(env, target);
    if (env->ExceptionCheck()) return nullptr;
    auto array = env->NewByteArray(static_cast<jsize>(bytes.size()));
    if (array) env->SetByteArrayRegion(array, 0, static_cast<jsize>(bytes.size()), reinterpret_cast<const jbyte*>(bytes.data()));
    return array;
}

extern "C" JNIEXPORT jint JNICALL Java_mod_runtime_JvmtiMethodTableRegression_hookCount(JNIEnv*, jclass) {
    return baselineHooks.load();
}

extern "C" JNIEXPORT jint JNICALL Java_mod_runtime_JvmtiMethodTableRegression_redefine(JNIEnv* env, jclass, jclass target, jbyteArray bytes) {
    auto jt = g_jvmti;
    if (!jt) return -1;
    jvmtiCapabilities caps{}; caps.can_redefine_classes = 1;
    auto err = jt->AddCapabilities(&caps);
    if (err) return err;
    auto data = env->GetByteArrayElements(bytes, nullptr);
    if (!data) return -2;
    jvmtiClassDefinition definition{target, env->GetArrayLength(bytes), reinterpret_cast<unsigned char*>(data)};
    err = jt->RedefineClasses(1, &definition);
    env->ReleaseByteArrayElements(bytes, data, JNI_ABORT);
    return err;
}

extern "C" JNIEXPORT jint JNICALL Java_mod_runtime_JvmtiMethodTableRegression_retransform(JNIEnv*, jclass, jclass target) {
    auto jt = g_jvmti;
    if (!jt) return -1;
    jvmtiCapabilities caps{}; caps.can_retransform_classes = 1;
    auto err = jt->AddCapabilities(&caps);
    return err ? err : jt->RetransformClasses(1, &target);
}

extern "C" JNIEXPORT jint JNICALL Java_mod_runtime_JvmtiMethodTableRegression_protect(JNIEnv*, jclass, jclass target) {
    return JvmtiMethodTable::Protect(target);
}

extern "C" JNIEXPORT jint JNICALL Java_mod_runtime_JvmtiMethodTableRegression_visible(JNIEnv* env, jclass, jclass target) {
    JvmtiMethodTable::Environment owned(g_jvm);
    auto jt = owned.get();
    if (!jt) return -1;
    jint count = 0; jclass* classes = nullptr;
    auto err = jt->GetLoadedClasses(&count, &classes);
    if (err) return -static_cast<jint>(err);
    int found = 0;
    for (jint i = 0; i < count; ++i) {
        if (env->IsSameObject(classes[i], target)) ++found;
        env->DeleteLocalRef(classes[i]);
    }
    jt->Deallocate(reinterpret_cast<unsigned char*>(classes));
    return found;
}

extern "C" JNIEXPORT jint JNICALL Java_mod_runtime_JvmtiMethodTableRegression_originalTableError(JNIEnv*, jclass) {
    jvmtiEnv* other = nullptr;
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&other), JVMTI_VERSION_1_2) != JNI_OK) return -1;
    jvmtiPhase phase{};
    auto err = other->GetPhase(&phase);

    auto cleanup = JvmtiMethodTable::original.DisposeEnvironment(other);
    return cleanup ? -static_cast<jint>(cleanup) : static_cast<jint>(err);
}

extern "C" JNIEXPORT jint JNICALL Java_mod_runtime_JvmtiMethodTableRegression_instances(JNIEnv*, jclass, jclass target) {
    return g_jvmti->IterateOverInstancesOfClass(target, JVMTI_HEAP_OBJECT_EITHER, nullptr, nullptr);
}

extern "C" JNIEXPORT void JNICALL Java_mod_runtime_JvmtiMethodTableRegression_changeTable(JNIEnv*, jclass) {
    JvmtiMethodTable::SetTable(g_jvmti, &JvmtiMethodTable::original);
}

extern "C" JNIEXPORT jboolean JNICALL Java_mod_runtime_JvmtiMethodTableRegression_tableRestored(JNIEnv*, jclass) {
    return JvmtiMethodTable::GetTable(g_jvmti) == &JvmtiMethodTable::wrapped;
}

extern "C" JNIEXPORT jint JNICALL Java_mod_runtime_JvmtiMethodTableRegression_plainEnvironment(JNIEnv* env, jclass) {
    JavaVM* java = nullptr; env->GetJavaVM(&java);
    jvmtiEnv* jt = nullptr;
    if (java->GetEnv(reinterpret_cast<void**>(&jt), JVMTI_VERSION_1_2) != JNI_OK) return -1;
    jvmtiPhase phase{};
    auto err = jt->GetPhase(&phase);
    auto cleanup = jt->DisposeEnvironment();
    return err ? err : cleanup;
}
