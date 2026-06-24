#include <oboe/Oboe.h>
#include <jni.h>
#include <atomic>
#include <cmath>
#include <memory>
#include <android/log.h>

// Howler native audio engine: opens an unprocessed Oboe input stream (config
// locked by the STEP ZERO probe — see docs/step-zero-results.md), computes RMS
// → dBFS per callback, and flags over-range. Calibration to SPL is applied
// upstream in Kotlin (tier-2 manual offset); this layer stays uncalibrated.

#define LOG_TAG "HowlerAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

using namespace oboe;

namespace {

constexpr float kOverThreshold = 0.999f;  // |sample| at/over full scale → clipping
constexpr float kFloorDbfs = -160.0f;     // silence floor

class HowlerEngine : public AudioStreamDataCallback {
public:
    DataCallbackResult onAudioReady(AudioStream *, void *audioData, int32_t numFrames) override {
        auto *samples = static_cast<float *>(audioData);
        double sumSquares = 0.0;
        bool over = false;
        for (int32_t i = 0; i < numFrames; ++i) {
            const float s = samples[i];
            sumSquares += static_cast<double>(s) * s;
            if (std::fabs(s) >= kOverThreshold) over = true;
        }
        const double rms = numFrames > 0 ? std::sqrt(sumSquares / numFrames) : 0.0;
        const float dbfs = rms > 1e-9 ? static_cast<float>(20.0 * std::log10(rms)) : kFloorDbfs;
        mLevelDbfs.store(dbfs);
        mOverRange.store(over);
        return DataCallbackResult::Continue;
    }

    bool start() {
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
        mLevelDbfs.store(kFloorDbfs);
        mOverRange.store(false);
    }

    float levelDbfs() const { return mLevelDbfs.load(); }
    bool overRange() const { return mOverRange.load(); }

private:
    std::shared_ptr<AudioStream> mStream;
    std::atomic<float> mLevelDbfs{kFloorDbfs};
    std::atomic<bool> mOverRange{false};
};

HowlerEngine gEngine;

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_howler_audio_AudioEngine_nativeStart(JNIEnv *, jobject) {
    return gEngine.start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_howler_audio_AudioEngine_nativeStop(JNIEnv *, jobject) {
    gEngine.stop();
}

JNIEXPORT jfloat JNICALL
Java_com_example_howler_audio_AudioEngine_nativeLevelDbfs(JNIEnv *, jobject) {
    return gEngine.levelDbfs();
}

JNIEXPORT jboolean JNICALL
Java_com_example_howler_audio_AudioEngine_nativeOverRange(JNIEnv *, jobject) {
    return gEngine.overRange() ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
