#include <jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>
#include <android/log.h>
#include <dlfcn.h>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>
#include <chrono>
#include <algorithm>

#define LOG_TAG "N64PlusLibretro"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

enum {
    RETRO_ENVIRONMENT_GET_CAN_DUPE = 3,
    RETRO_ENVIRONMENT_SET_PIXEL_FORMAT = 10,
    RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME = 18,
    RETRO_ENVIRONMENT_SET_GEOMETRY = 37,
    RETRO_PIXEL_FORMAT_XRGB8888 = 1,
    RETRO_DEVICE_JOYPAD = 1
};

struct retro_game_info { const char* path; const void* data; size_t size; const char* meta; };
struct retro_game_geometry { unsigned base_width, base_height, max_width, max_height; float aspect_ratio; };
struct retro_system_timing { double fps, sample_rate; };
struct retro_system_av_info { retro_game_geometry geometry; retro_system_timing timing; };

using environment_t = bool (*)(unsigned, void*);
using video_t = void (*)(const void*, unsigned, unsigned, size_t);
using audio_sample_t = void (*)(int16_t, int16_t);
using audio_batch_t = size_t (*)(const int16_t*, size_t);
using input_poll_t = void (*)();
using input_state_t = int16_t (*)(unsigned, unsigned, unsigned, unsigned);

struct CoreApi {
    unsigned (*api_version)() = nullptr;
    void (*set_environment)(environment_t) = nullptr;
    void (*set_video_refresh)(video_t) = nullptr;
    void (*set_audio_sample)(audio_sample_t) = nullptr;
    void (*set_audio_sample_batch)(audio_batch_t) = nullptr;
    void (*set_input_poll)(input_poll_t) = nullptr;
    void (*set_input_state)(input_state_t) = nullptr;
    void (*init)() = nullptr;
    void (*deinit)() = nullptr;
    bool (*load_game)(const retro_game_info*) = nullptr;
    void (*unload_game)() = nullptr;
    void (*run)() = nullptr;
    void (*get_system_av_info)(retro_system_av_info*) = nullptr;
};

static CoreApi core;
static void* coreHandle = nullptr;
static ANativeWindow* windowHandle = nullptr;
static JavaVM* javaVm = nullptr;
static jobject javaActivity = nullptr;
static jmethodID audioMethod = nullptr;
static std::thread emulationThread;
static std::atomic<bool> running{false};
static std::atomic<uint16_t> joypadMask{0};
static std::mutex coreMutex;
static double frameRate = 60.0, audioRate = 32000.0;
static bool coreInitialized = false;
static bool gameLoaded = false;
static unsigned configuredWidth = 0, configuredHeight = 0;

static bool environmentCallback(unsigned command, void* data) {
    switch (command) {
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            if (!data) return false;
            *static_cast<unsigned*>(data) = RETRO_PIXEL_FORMAT_XRGB8888;
            return true;
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            if (data) *static_cast<bool*>(data) = true;
            return true;
        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
            return true;
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
            return data != nullptr;
        default:
            return false;
    }
}

static void videoCallback(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (!data || !windowHandle || width == 0 || height == 0) return;
    if (width != configuredWidth || height != configuredHeight) {
        ANativeWindow_setBuffersGeometry(windowHandle, static_cast<int32_t>(width),
                                         static_cast<int32_t>(height), WINDOW_FORMAT_RGBA_8888);
        configuredWidth = width;
        configuredHeight = height;
    }
    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(windowHandle, &buffer, nullptr) != 0) return;
    const auto* src = static_cast<const uint8_t*>(data);
    auto* dst = static_cast<uint32_t*>(buffer.bits);
    const unsigned copyW = std::min(width, static_cast<unsigned>(buffer.width));
    const unsigned copyH = std::min(height, static_cast<unsigned>(buffer.height));
    for (unsigned y = 0; y < copyH; ++y) {
        const auto* row = reinterpret_cast<const uint32_t*>(src + y * pitch);
        auto* out = dst + y * buffer.stride;
        for (unsigned x = 0; x < copyW; ++x) {
            const uint32_t px = row[x];
            const uint32_t r = (px >> 16) & 0xff;
            const uint32_t g = (px >> 8) & 0xff;
            const uint32_t b = px & 0xff;
            out[x] = r | (g << 8) | (b << 16) | 0xff000000u;
        }
    }
    ANativeWindow_unlockAndPost(windowHandle);
}

static size_t audioBatchCallback(const int16_t* samples, size_t frames) {
    if (!samples || frames == 0 || !javaVm || !javaActivity || !audioMethod) return frames;
    JNIEnv* env = nullptr;
    bool attached = false;
    const jint state = javaVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (state == JNI_EDETACHED) {
        if (javaVm->AttachCurrentThread(&env, nullptr) != JNI_OK) return frames;
        attached = true;
    } else if (state != JNI_OK) return frames;

    const jsize count = static_cast<jsize>(std::min<size_t>(frames * 2, 32768));
    jshortArray array = env->NewShortArray(count);
    if (array) {
        env->SetShortArrayRegion(array, 0, count, reinterpret_cast<const jshort*>(samples));
        env->CallVoidMethod(javaActivity, audioMethod, array, count);
        env->DeleteLocalRef(array);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    if (attached) javaVm->DetachCurrentThread();
    return frames;
}

static void audioSampleCallback(int16_t left, int16_t right) {
    const int16_t pair[2] = {left, right};
    audioBatchCallback(pair, 1);
}
static void inputPollCallback() {}
static int16_t inputStateCallback(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port != 0 || device != RETRO_DEVICE_JOYPAD || index != 0 || id > 15) return 0;
    return (joypadMask.load() & (1u << id)) ? 1 : 0;
}

template <typename T> static bool loadSymbol(T& target, const char* name) {
    target = reinterpret_cast<T>(dlsym(coreHandle, name));
    if (!target) LOGE("Missing Libretro symbol: %s", name);
    return target != nullptr;
}

static void releaseCore() {
    running.store(false);
    if (emulationThread.joinable() && emulationThread.get_id() != std::this_thread::get_id())
        emulationThread.join();
    if (gameLoaded && core.unload_game) { core.unload_game(); gameLoaded = false; }
    if (coreInitialized && core.deinit) { core.deinit(); coreInitialized = false; }
    core = CoreApi{};
    if (coreHandle) { dlclose(coreHandle); coreHandle = nullptr; }
    if (windowHandle) { ANativeWindow_release(windowHandle); windowHandle = nullptr; }
    if (javaActivity && javaVm) {
        JNIEnv* env = nullptr;
        if (javaVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK)
            env->DeleteGlobalRef(javaActivity);
        javaActivity = nullptr;
    }
    audioMethod = nullptr;
    configuredWidth = configuredHeight = 0;
    joypadMask.store(0);
}

static void runLoop() {
    using clock = std::chrono::steady_clock;
    const auto frameDuration = std::chrono::duration<double>(1.0 / std::max(1.0, frameRate));
    auto next = clock::now();
    while (running.load()) {
        core.run();
        next += std::chrono::duration_cast<clock::duration>(frameDuration);
        const auto now = clock::now();
        if (next > now) std::this_thread::sleep_until(next);
        else if (now - next > frameDuration * 3) next = now;
    }
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_sfj_n64plus_EmulatorActivity_nativePrepare(JNIEnv* env, jobject thiz,
        jstring corePath, jstring gamePath, jobject surface) {
    std::lock_guard<std::mutex> guard(coreMutex);
    releaseCore();
    if (!corePath || !gamePath || !surface) return -1.0;
    const char* cp = env->GetStringUTFChars(corePath, nullptr);
    const char* gp = env->GetStringUTFChars(gamePath, nullptr);
    if (!cp || !gp) {
        if (cp) env->ReleaseStringUTFChars(corePath, cp);
        if (gp) env->ReleaseStringUTFChars(gamePath, gp);
        return -1.0;
    }
    coreHandle = dlopen(cp, RTLD_NOW | RTLD_LOCAL);
    env->ReleaseStringUTFChars(corePath, cp);
    if (!coreHandle) { env->ReleaseStringUTFChars(gamePath, gp); return -1.0; }

    bool ok = true;
    ok &= loadSymbol(core.api_version, "retro_api_version");
    ok &= loadSymbol(core.set_environment, "retro_set_environment");
    ok &= loadSymbol(core.set_video_refresh, "retro_set_video_refresh");
    ok &= loadSymbol(core.set_audio_sample, "retro_set_audio_sample");
    ok &= loadSymbol(core.set_audio_sample_batch, "retro_set_audio_sample_batch");
    ok &= loadSymbol(core.set_input_poll, "retro_set_input_poll");
    ok &= loadSymbol(core.set_input_state, "retro_set_input_state");
    ok &= loadSymbol(core.init, "retro_init");
    ok &= loadSymbol(core.deinit, "retro_deinit");
    ok &= loadSymbol(core.load_game, "retro_load_game");
    ok &= loadSymbol(core.unload_game, "retro_unload_game");
    ok &= loadSymbol(core.run, "retro_run");
    ok &= loadSymbol(core.get_system_av_info, "retro_get_system_av_info");
    if (!ok || core.api_version() != 1) {
        env->ReleaseStringUTFChars(gamePath, gp);
        releaseCore();
        return -1.0;
    }

    windowHandle = ANativeWindow_fromSurface(env, surface);
    if (!windowHandle) {
        env->ReleaseStringUTFChars(gamePath, gp);
        releaseCore();
        return -1.0;
    }
    env->GetJavaVM(&javaVm);
    javaActivity = env->NewGlobalRef(thiz);
    jclass cls = env->GetObjectClass(thiz);
    audioMethod = env->GetMethodID(cls, "onAudioSamples", "([SI)V");
    env->DeleteLocalRef(cls);
    if (!audioMethod) {
        env->ReleaseStringUTFChars(gamePath, gp);
        releaseCore();
        return -1.0;
    }

    core.set_environment(environmentCallback);
    core.set_video_refresh(videoCallback);
    core.set_audio_sample(audioSampleCallback);
    core.set_audio_sample_batch(audioBatchCallback);
    core.set_input_poll(inputPollCallback);
    core.set_input_state(inputStateCallback);
    core.init();
    coreInitialized = true;

    retro_game_info info{};
    info.path = gp;
    const bool loaded = core.load_game(&info);
    env->ReleaseStringUTFChars(gamePath, gp);
    if (!loaded) { releaseCore(); return -1.0; }
    gameLoaded = true;

    retro_system_av_info av{};
    core.get_system_av_info(&av);
    frameRate = av.timing.fps > 1.0 ? av.timing.fps : 60.0;
    audioRate = av.timing.sample_rate > 8000.0 ? av.timing.sample_rate : 32000.0;
    return audioRate;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sfj_n64plus_EmulatorActivity_nativeRun(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> guard(coreMutex);
    if (!core.run || running.load()) return;
    running.store(true);
    emulationThread = std::thread(runLoop);
}
extern "C" JNIEXPORT void JNICALL
Java_com_sfj_n64plus_EmulatorActivity_nativeSetInput(JNIEnv*, jobject, jint mask) {
    joypadMask.store(static_cast<uint16_t>(mask));
}
extern "C" JNIEXPORT void JNICALL
Java_com_sfj_n64plus_EmulatorActivity_nativeStop(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> guard(coreMutex);
    releaseCore();
}