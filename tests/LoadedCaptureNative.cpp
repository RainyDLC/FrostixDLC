#include "../build/release/mod_payload.cpp"

extern "C" JNIEXPORT jbyteArray JNICALL Java_mod_runtime_LoadedCaptureRegression_capture(
        JNIEnv* env, jclass, jclass target) {
    env->GetJavaVM(&g_jvm);
    if (!g_jvmti && g_jvm->GetEnv((void**)&g_jvmti, JVMTI_VERSION_1_2) != JNI_OK) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "JVMTI environment unavailable");
        return nullptr;
    }
    auto data = GetOriginalBytes(env, target);
    if (env->ExceptionCheck()) return nullptr;
    auto result = env->NewByteArray((jsize)data.size());
    if (result) env->SetByteArrayRegion(result, 0, (jsize)data.size(), (const jbyte*)data.data());
    return result;
}
extern "C" JNIEXPORT jint JNICALL Java_mod_runtime_LoadedCaptureRegression_redefine(
        JNIEnv* env, jclass, jclass target, jbyteArray bytes) {
    jvmtiEnv* jt = nullptr;
    g_jvm->GetEnv((void**)&jt, JVMTI_VERSION_1_2);
    if (!jt) return -1;
    jvmtiCapabilities caps{};
    caps.can_redefine_classes = 1;
    auto err = jt->AddCapabilities(&caps);
    if (err == JVMTI_ERROR_NONE) {
        auto data = env->GetByteArrayElements(bytes, nullptr);
        jvmtiClassDefinition def{target, env->GetArrayLength(bytes), (unsigned char*)data};
        err = jt->RedefineClasses(1, &def);
        env->ReleaseByteArrayElements(bytes, data, JNI_ABORT);
    }
    jt->DisposeEnvironment();
    return err;
}
