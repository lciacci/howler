#include <oboe/Oboe.h>
#include <jni.h>
#include <memory>
#include <android/log.h>

#include "meter_core.h"

// Howler native audio engine (Android): opens an unprocessed Oboe input stream
// (config locked by the STEP ZERO probe — see docs/step-zero-results.md) and
// feeds each callback to the platform-agnostic MeterCore (meter_core.h), which
// owns all DSP and statistics. This file is the Android I/O + JNI shell only;
// the iOS port reuses meter_core.h behind an AVAudioEngine tap.

#define LOG_TAG "HowlerAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

using namespace oboe;

namespace {

class HowlerEngine : public AudioStreamDataCallback {
public:
    DataCallbackResult onAudioReady(AudioStream *, void *audioData, int32_t numFrames) override {
        mCore.process(static_cast<float *>(audioData), numFrames);
        return DataCallbackResult::Continue;
    }

    bool start() {
        if (mStream) return true;  // already open — ignore redundant RESUME
        AudioStreamBuilder builder;
        builder.setDirection(Direction::Input)
            ->setPerformanceMode(PerformanceMode::LowLatency)
            ->setSharingMode(SharingMode::Exclusive)
            ->setFormat(AudioFormat::Float)
            ->setChannelCount(1)
            ->setSampleRate(48000)
            ->setInputPreset(InputPreset::Unprocessed)
            ->setDataCallback(this);
        Result result = builder.openStream(mStream);
        if (result != Result::OK) {
            LOGI("openStream failed: %s", convertToText(result));
            return false;
        }
        // setSampleRate() is a request — init the DSP against the rate Oboe
        // actually granted, or the A-weighting and Fast/Slow timing would be
        // wrong on any device/route that opens at a different rate.
        mCore.configure(mStream->getSampleRate());
        result = mStream->requestStart();
        if (result != Result::OK) {
            LOGI("requestStart failed: %s", convertToText(result));
            mStream->close();
            mStream.reset();
            return false;
        }
        LOGI("started: preset=%d sr=%d frames=%d", static_cast<int>(mStream->getInputPreset()),
             mStream->getSampleRate(), mStream->getFramesPerBurst());
        return true;
    }

    void stop() {
        if (mStream) {
            mStream->requestStop();
            mStream->close();
            mStream.reset();
        }
        mCore.onStopped();
    }

    howler::MeterCore &core() { return mCore; }

private:
    std::shared_ptr<AudioStream> mStream;
    howler::MeterCore mCore;
};

HowlerEngine gEngine;

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_houseofyeti_howler_audio_AudioEngine_nativeStart(JNIEnv *, jobject) {
    return gEngine.start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_houseofyeti_howler_audio_AudioEngine_nativeStop(JNIEnv *, jobject) {
    gEngine.stop();
}

JNIEXPORT jfloat JNICALL
Java_com_houseofyeti_howler_audio_AudioEngine_nativeLevelDbfs(JNIEnv *, jobject) {
    return gEngine.core().levelDbfs();
}

JNIEXPORT jboolean JNICALL
Java_com_houseofyeti_howler_audio_AudioEngine_nativeOverRange(JNIEnv *, jobject) {
    return gEngine.core().overRange() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_houseofyeti_howler_audio_AudioEngine_nativeSetFast(JNIEnv *, jobject, jboolean fast) {
    gEngine.core().setFast(fast == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_houseofyeti_howler_audio_AudioEngine_nativeSetWeighting(JNIEnv *, jobject, jint w) {
    gEngine.core().setWeighting(w);
}

JNIEXPORT jfloat JNICALL
Java_com_houseofyeti_howler_audio_AudioEngine_nativeMaxDbfs(JNIEnv *, jobject) {
    return gEngine.core().maxDbfs();
}

JNIEXPORT jboolean JNICALL
Java_com_houseofyeti_howler_audio_AudioEngine_nativeMaxClipped(JNIEnv *, jobject) {
    return gEngine.core().maxClipped() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloat JNICALL
Java_com_houseofyeti_howler_audio_AudioEngine_nativeMinDbfs(JNIEnv *, jobject) {
    return gEngine.core().minDbfs();
}

JNIEXPORT jfloat JNICALL
Java_com_houseofyeti_howler_audio_AudioEngine_nativeLeqDbfs(JNIEnv *, jobject) {
    return gEngine.core().leqDbfs();
}

JNIEXPORT void JNICALL
Java_com_houseofyeti_howler_audio_AudioEngine_nativeResetStats(JNIEnv *, jobject) {
    gEngine.core().resetStats();
}

}  // extern "C"
