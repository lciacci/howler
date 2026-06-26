#include <oboe/Oboe.h>
#include <jni.h>
#include <atomic>
#include <cmath>
#include <complex>
#include <cstdint>
#include <memory>
#include <android/log.h>

// Howler native audio engine: opens an unprocessed Oboe input stream (config
// locked by the STEP ZERO probe — see docs/step-zero-results.md), computes RMS
// → dBFS per callback (both Z = flat and A = A-weighted), and flags over-range.
// Calibration to SPL is applied upstream in Kotlin (tier-2 manual offset); this
// layer stays uncalibrated.

#define LOG_TAG "HowlerAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

using namespace oboe;

namespace {

constexpr float kOverThreshold = 0.999f;  // |sample| at/over full scale → clipping
constexpr float kFloorDbfs = -160.0f;     // silence floor
constexpr float kMinSentinel = 200.0f;    // Min-hold "no reading yet" (above any real dB)

// A-weighting (IEC 61672). Three biquads in cascade, coefficients derived at
// init by bilinear transform of the analog prototype — no magic numbers, only
// the four standard corner frequencies. Run in double precision (the 20.6 Hz
// pole sits very close to z=1 at 48 kHz). Cascade gain normalized to 0 dB at
// 1 kHz numerically, so we never hand-carry the A1000 constant.
struct Biquad {
    double b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;  // a0 normalized to 1
    double z1 = 0, z2 = 0;                           // DF2-transposed state

    double process(double x) {
        const double y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }
    void reset() { z1 = z2 = 0; }

    std::complex<double> responseAt(double omega) const {
        const std::complex<double> z1i = std::polar(1.0, -omega);  // z^-1
        const std::complex<double> z2i = z1i * z1i;
        return (b0 + b1 * z1i + b2 * z2i) / (1.0 + a1 * z1i + a2 * z2i);
    }
};

// Bilinear-transform an analog biquad N(s)/D(s) with coefficients
// N = nb2·s² + nb1·s + nb0, D = s² + da1·s + da0 (da2 == 1), at sample rate fs.
Biquad bilinear(double nb2, double nb1, double nb0, double da1, double da0, double fs) {
    const double c = 2.0 * fs;
    const double c2 = c * c;
    const double B0 = nb2 * c2 + nb1 * c + nb0;
    const double B1 = 2.0 * (nb0 - nb2 * c2);
    const double B2 = nb2 * c2 - nb1 * c + nb0;
    const double A0 = c2 + da1 * c + da0;
    const double A1 = 2.0 * (da0 - c2);
    const double A2 = c2 - da1 * c + da0;
    return {B0 / A0, B1 / A0, B2 / A0, A1 / A0, A2 / A0};
}

class AWeighting {
public:
    void init(double fs) {
        constexpr double kTwoPi = 6.283185307179586;
        const double w1 = kTwoPi * 20.598997;
        const double w2 = kTwoPi * 107.65265;
        const double w3 = kTwoPi * 737.86223;
        const double w4 = kTwoPi * 12194.217;
        // 4 zeros at s=0, poles {w1²,w2,w3,w4²}; split into 3 biquads.
        mSec[0] = bilinear(1, 0, 0, 2 * w1, w1 * w1, fs);              // s² / (s+w1)²
        mSec[1] = bilinear(1, 0, 0, w2 + w3, w2 * w3, fs);            // s² / (s+w2)(s+w3)
        mSec[2] = bilinear(0, 0, 1, 2 * w4, w4 * w4, fs);            // 1  / (s+w4)²
        // Normalize cascade to unity gain at 1 kHz.
        const double omega1k = kTwoPi * 1000.0 / fs;
        std::complex<double> h = 1.0;
        for (const auto &s : mSec) h *= s.responseAt(omega1k);
        mGain = 1.0 / std::abs(h);
        reset();
    }

    double process(double x) {
        double y = x * mGain;
        for (auto &s : mSec) y = s.process(y);
        return y;
    }

    void reset() { for (auto &s : mSec) s.reset(); }

private:
    Biquad mSec[3];
    double mGain = 1.0;
};

class HowlerEngine : public AudioStreamDataCallback {
public:
    DataCallbackResult onAudioReady(AudioStream *, void *audioData, int32_t numFrames) override {
        // Stats reset is requested from the UI thread but applied here, on the
        // audio thread, so the accumulators are only ever touched by one thread.
        if (mResetRequested.exchange(false)) clearStats();
        auto *samples = static_cast<float *>(audioData);
        // IEC 61672 exponential time-weighting: one-pole smoother on the
        // mean-square with k = 1 - exp(-1/(fs·τ)). Fast τ=125 ms, Slow τ=1 s.
        const double k = mFast.load() ? mKFast : mKSlow;
        bool over = false;
        double blockSumZ = 0.0, blockSumA = 0.0;
        for (int32_t i = 0; i < numFrames; ++i) {
            const float s = samples[i];
            const double a = mWeightA.process(s);  // stateful — run every sample
            const double sq = static_cast<double>(s) * s;
            const double aq = a * a;
            blockSumZ += sq;
            blockSumA += aq;
            mLeqSumZ += sq;  // unweighted energy sum, for Leq
            mLeqSumA += aq;
            if (mPrimed) { mMsZ += (sq - mMsZ) * k; mMsA += (aq - mMsA) * k; }
            if (std::fabs(s) >= kOverThreshold) over = true;  // clip detect on raw signal
        }
        // Prime the smoother to the first block's mean-square, not a single
        // sample (a sample near a zero-crossing reads ~floor and, with Slow's
        // tiny coefficient, would never recover — corrupting Min-hold).
        if (!mPrimed && numFrames > 0) {
            mMsZ = blockSumZ / numFrames;
            mMsA = blockSumA / numFrames;
            mPrimed = true;
        }
        mLeqCount += numFrames;
        const float levelZ = msToDbfs(mMsZ);
        const float levelA = msToDbfs(mMsA);
        mLevelDbfs.store(levelZ);
        mLevelDbfsA.store(levelA);
        mOverRange.store(over);
        // Max-hold: peak of the time-weighted level. A clipped block reads HIGH
        // (samples pinned near full scale) — exactly the loud events the peak
        // exists to catch — so it is included, not skipped; the value is a lower
        // bound on the true (clipped) peak.
        if (levelZ > mMaxDbfs.load()) mMaxDbfs.store(levelZ);
        if (levelA > mMaxDbfsA.load()) mMaxDbfsA.store(levelA);
        // Min-hold: quietest time-weighted level since reset.
        if (levelZ < mMinDbfs.load()) mMinDbfs.store(levelZ);
        if (levelA < mMinDbfsA.load()) mMinDbfsA.store(levelA);
        // Leq: equivalent continuous level = 10·log10(mean energy since reset).
        // Independent of Fast/Slow (raw energy average, no smoothing).
        mLeqDbfs.store(msToDbfs(mLeqSumZ / mLeqCount));
        mLeqDbfsA.store(msToDbfs(mLeqSumA / mLeqCount));
        return DataCallbackResult::Continue;
    }

    // Smoothed mean-square → dBFS. ms = rms², so 20·log10(rms) = 10·log10(ms).
    static float msToDbfs(double ms) {
        return ms > 1e-18 ? static_cast<float>(10.0 * std::log10(ms)) : kFloorDbfs;
    }

    // Clear all since-reset statistics + smoother state. Audio thread only.
    void clearStats() {
        mMsZ = mMsA = 0.0;
        mPrimed = false;
        mLeqSumZ = mLeqSumA = 0.0;
        mLeqCount = 0;
        mMaxDbfs.store(kFloorDbfs);
        mMaxDbfsA.store(kFloorDbfs);
        mMinDbfs.store(kMinSentinel);
        mMinDbfsA.store(kMinSentinel);
        mLeqDbfs.store(kFloorDbfs);
        mLeqDbfsA.store(kFloorDbfs);
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
        // wrong on any device/route that opens at a different rate. Stats
        // (Max/Min/Leq) and the smoother are NOT reset here: they must survive a
        // lifecycle stop/restart; only the UI/config resets them.
        const double fs = mStream->getSampleRate();
        mWeightA.init(fs);
        mKFast = 1.0 - std::exp(-1.0 / (fs * 0.125));
        mKSlow = 1.0 - std::exp(-1.0 / (fs * 1.000));
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
        mLevelDbfsA.store(kFloorDbfs);
        mOverRange.store(false);
    }

    float levelDbfs() const { return mLevelDbfs.load(); }
    float levelDbfsA() const { return mLevelDbfsA.load(); }
    float maxDbfs() const { return mMaxDbfs.load(); }
    float maxDbfsA() const { return mMaxDbfsA.load(); }
    float minDbfs() const { return mMinDbfs.load(); }
    float minDbfsA() const { return mMinDbfsA.load(); }
    float leqDbfs() const { return mLeqDbfs.load(); }
    float leqDbfsA() const { return mLeqDbfsA.load(); }
    bool overRange() const { return mOverRange.load(); }
    void setFast(bool fast) { mFast.store(fast); }
    void resetStats() { mResetRequested.store(true); }  // applied on the audio thread

private:
    std::shared_ptr<AudioStream> mStream;
    AWeighting mWeightA;
    double mMsZ = 0.0, mMsA = 0.0;       // smoothed mean-square (audio thread only)
    double mKFast = 0.0, mKSlow = 0.0;   // smoothing coefficients, set in start()
    bool mPrimed = false;                // smoother seeded? (audio thread only)
    double mLeqSumZ = 0.0, mLeqSumA = 0.0;  // energy sums since reset (audio thread only)
    uint64_t mLeqCount = 0;                 // samples summed (audio thread only)
    std::atomic<bool> mFast{true};       // Fast (125 ms) is the default
    std::atomic<bool> mResetRequested{false};
    std::atomic<float> mLevelDbfs{kFloorDbfs};
    std::atomic<float> mLevelDbfsA{kFloorDbfs};
    std::atomic<float> mMaxDbfs{kFloorDbfs};
    std::atomic<float> mMaxDbfsA{kFloorDbfs};
    std::atomic<float> mMinDbfs{kMinSentinel};
    std::atomic<float> mMinDbfsA{kMinSentinel};
    std::atomic<float> mLeqDbfs{kFloorDbfs};
    std::atomic<float> mLeqDbfsA{kFloorDbfs};
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

JNIEXPORT jfloat JNICALL
Java_com_example_howler_audio_AudioEngine_nativeLevelDbfsA(JNIEnv *, jobject) {
    return gEngine.levelDbfsA();
}

JNIEXPORT jboolean JNICALL
Java_com_example_howler_audio_AudioEngine_nativeOverRange(JNIEnv *, jobject) {
    return gEngine.overRange() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_howler_audio_AudioEngine_nativeSetFast(JNIEnv *, jobject, jboolean fast) {
    gEngine.setFast(fast == JNI_TRUE);
}

JNIEXPORT jfloat JNICALL
Java_com_example_howler_audio_AudioEngine_nativeMaxDbfs(JNIEnv *, jobject) {
    return gEngine.maxDbfs();
}

JNIEXPORT jfloat JNICALL
Java_com_example_howler_audio_AudioEngine_nativeMaxDbfsA(JNIEnv *, jobject) {
    return gEngine.maxDbfsA();
}

JNIEXPORT jfloat JNICALL
Java_com_example_howler_audio_AudioEngine_nativeMinDbfs(JNIEnv *, jobject) {
    return gEngine.minDbfs();
}

JNIEXPORT jfloat JNICALL
Java_com_example_howler_audio_AudioEngine_nativeMinDbfsA(JNIEnv *, jobject) {
    return gEngine.minDbfsA();
}

JNIEXPORT jfloat JNICALL
Java_com_example_howler_audio_AudioEngine_nativeLeqDbfs(JNIEnv *, jobject) {
    return gEngine.leqDbfs();
}

JNIEXPORT jfloat JNICALL
Java_com_example_howler_audio_AudioEngine_nativeLeqDbfsA(JNIEnv *, jobject) {
    return gEngine.leqDbfsA();
}

JNIEXPORT void JNICALL
Java_com_example_howler_audio_AudioEngine_nativeResetStats(JNIEnv *, jobject) {
    gEngine.resetStats();
}

}  // extern "C"
