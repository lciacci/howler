// Headless check that meter_core.h is portable stdlib C++ and numerically sane,
// exercised THROUGH the C bridge (so the shim is covered too). Runs on the macOS
// host — no Xcode, no device. The iOS target differs only by SDK sysroot.
//
// Not part of the Xcode target (it has its own main()) — a standalone headless
// check. Build + run (from ios/HowlerMeter/HowlerMeter/):
//   clang++ -std=c++17 -O2 -Wall -I ../../../app/src/main/cpp \
//       test_host.cpp meter_bridge.cpp -o /tmp/howler_meter_test && /tmp/howler_meter_test

#include "meter_bridge.h"

#include <cassert>
#include <cmath>
#include <cstdio>
#include <vector>

namespace {
constexpr double kSr = 48000.0;
constexpr int kBlock = 480;   // 10 ms
constexpr double kTwoPi = 6.283185307179586;

// Feed `seconds` of a 1 kHz sine at `amp` through the meter in 10 ms blocks.
void feedSine(MeterHandle *m, double amp, double seconds) {
    std::vector<float> buf(kBlock);
    const int blocks = static_cast<int>(seconds * kSr / kBlock);
    static double phase = 0.0;
    const double dp = kTwoPi * 1000.0 / kSr;
    for (int b = 0; b < blocks; ++b) {
        for (int i = 0; i < kBlock; ++i) {
            buf[i] = static_cast<float>(amp * std::sin(phase));
            phase += dp;
        }
        meter_process(m, buf.data(), kBlock);
    }
}

bool near(float a, double b, double tol) { return std::fabs(a - b) <= tol; }
}  // namespace

int main() {
    MeterHandle *m = meter_create();
    meter_configure(m, kSr);

    // 1 kHz sine, amp 0.5 → RMS = 0.5/√2 → dBFS = 20·log10(0.35355) = -9.03.
    const double kExpected = 20.0 * std::log10(0.5 / std::sqrt(2.0));

    meter_set_weighting(m, 0);  // Z (flat)
    meter_reset_stats(m);
    feedSine(m, 0.5, 2.0);      // 2 s >> Fast τ=125 ms → smoother settled
    printf("Z level = %.2f dBFS (expect %.2f)\n", meter_level_dbfs(m), kExpected);
    assert(near(meter_level_dbfs(m), kExpected, 0.1) && "Z-weight 1kHz level");
    assert(!meter_over_range(m) && "amp 0.5 must not trip over-range");
    assert(meter_max_dbfs(m) >= meter_level_dbfs(m) && "max >= live");
    assert(meter_min_dbfs(m) <= meter_level_dbfs(m) && "min <= live");

    meter_set_weighting(m, 1);  // A — normalized to 0 dB at 1 kHz → same level
    feedSine(m, 0.5, 2.0);
    printf("A level = %.2f dBFS (expect %.2f)\n", meter_level_dbfs(m), kExpected);
    assert(near(meter_level_dbfs(m), kExpected, 0.2) && "A-weight 1kHz ~= Z at 1kHz");

    meter_reset_stats(m);
    feedSine(m, 1.0, 0.5);      // full-scale → clip
    printf("over=%d maxClipped=%d\n", meter_over_range(m), meter_max_clipped(m));
    assert(meter_over_range(m) && "amp 1.0 must trip over-range");
    assert(meter_max_clipped(m) && "clipped block must flag Max");

    meter_on_stopped(m);
    assert(near(meter_level_dbfs(m), -160.0, 0.01) && "stop floors live level");

    meter_destroy(m);
    printf("PASS: meter_core.h ports clean + numerically sane via C bridge\n");
    return 0;
}
