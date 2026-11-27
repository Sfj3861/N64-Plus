#include <jni.h>
#include <dlfcn.h>
#include <string>

static void* coreHandle = nullptr;

extern "C" JNIEXPORT jstring JNICALL
Java_com_sfj_n64plus_EmulatorActivity_nativeProbeCore(JNIEnv* env, jobject, jstring path) {
    if (path == nullptr) return env->NewStringUTF("Core path was not provided.");
    if (coreHandle != nullptr) {
        dlclose(coreHandle);
        coreHandle = nullptr;
    }
    const char* p = env->GetStringUTFChars(path, nullptr);
    if (p == nullptr) return env->NewStringUTF("Could not read core path.");

    dlerror();
    void* handle = dlopen(p, RTLD_NOW | RTLD_LOCAL);
    const char* loadError = dlerror();
    std::string result;
    if (handle == nullptr) {
        result = std::string("Core library could not be loaded: ") +
                 (loadError != nullptr ? loadError : "unknown dynamic-loader error");
    } else {
        const char* required[] = {
            "retro_api_version", "retro_set_environment", "retro_set_video_refresh",
            "retro_set_audio_sample", "retro_set_audio_sample_batch", "retro_set_input_poll",
            "retro_set_input_state", "retro_init", "retro_deinit", "retro_load_game",
            "retro_unload_game", "retro_run", "retro_get_system_info", "retro_get_system_av_info"
        };
        bool ok = true;
        for (const char* symbol : required) {
            dlerror();
            if (dlsym(handle, symbol) == nullptr) {
                ok = false;
                result += std::string("Missing Libretro symbol: ") + symbol + "\n";
            }
        }
        if (ok) {
            coreHandle = handle;
            result = "Compatible Libretro entry points found. Core library loaded; initialization, game loading and Android AV callbacks are not wired up yet.";
        } else {
            dlclose(handle);
            result = "The library is not a complete Libretro core.\n" + result;
        }
    }
    env->ReleaseStringUTFChars(path, p);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_sfj_n64plus_EmulatorActivity_nativeCloseCore(JNIEnv*, jobject) {
    if (coreHandle != nullptr) {
        dlclose(coreHandle);
        coreHandle = nullptr;
    }
}