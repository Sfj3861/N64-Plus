#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <string>
#define LOG_TAG "N64Plus"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
static void* coreHandle = nullptr;
extern "C" JNIEXPORT jstring JNICALL
Java_com_sfj_n64plus_EmulatorActivity_nativeProbeCore(JNIEnv* env, jobject, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    coreHandle = dlopen(p, RTLD_NOW | RTLD_LOCAL);
    std::string result;
    if (!coreHandle) result = std::string("Core library unavailable: ") + dlerror();
    else {
        const char* required[] = {"retro_init","retro_deinit","retro_load_game","retro_run","retro_get_system_info","retro_get_system_av_info"};
        bool ok = true;
        for (const char* symbol : required) if (!dlsym(coreHandle, symbol)) { ok=false; result += std::string("Missing symbol: ")+symbol+"; "; }
        if (ok) result = "Core library symbols found. Frontend callbacks and game loop are not yet implemented.";
    }
    env->ReleaseStringUTFChars(path, p);
    return env->NewStringUTF(result.c_str());
}
extern "C" JNIEXPORT void JNICALL
Java_com_sfj_n64plus_EmulatorActivity_nativeCloseCore(JNIEnv*, jobject) {
    if (coreHandle) { dlclose(coreHandle); coreHandle=nullptr; }
}
