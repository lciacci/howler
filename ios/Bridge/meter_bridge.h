#ifndef HOWLER_METER_BRIDGE_H
#define HOWLER_METER_BRIDGE_H

// C ABI over howler::MeterCore (app/src/main/cpp/meter_core.h — the shared DSP,
// one header for both platforms). Swift cannot call C++ classes directly across
// all toolchains, so this flat C shim is the bridge: the SwiftUI app imports this
// header via the bridging header, AVAudioEngine feeds meter_process, and the
// getters mirror the Android JNI surface one-to-one.

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct MeterHandle MeterHandle;

MeterHandle *meter_create(void);
void meter_destroy(MeterHandle *);

// Init filters + time weighting for the sample rate the audio session granted.
void meter_configure(MeterHandle *, double sample_rate);
// One tap buffer of mono float samples. Audio (render) thread only.
void meter_process(MeterHandle *, const float *samples, int32_t num_frames);
// Live readout invalid after stop; since-reset stats persist.
void meter_on_stopped(MeterHandle *);

float meter_level_dbfs(const MeterHandle *);
float meter_max_dbfs(const MeterHandle *);
bool meter_max_clipped(const MeterHandle *);
float meter_min_dbfs(const MeterHandle *);
float meter_leq_dbfs(const MeterHandle *);
bool meter_over_range(const MeterHandle *);

void meter_set_fast(MeterHandle *, bool fast);
void meter_set_weighting(MeterHandle *, int32_t w);  // 0=Z, 1=A, 2=C
void meter_reset_stats(MeterHandle *);

#ifdef __cplusplus
}
#endif

#endif  // HOWLER_METER_BRIDGE_H
