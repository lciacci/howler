// C-shim implementation over howler::MeterCore. Reaches the shared DSP header in
// the Android tree directly (../app/src/main/cpp) — the whole point of the spike
// is that ONE header serves both platforms, so we do not copy it.

#include "meter_bridge.h"

#include "../app/src/main/cpp/meter_core.h"

using howler::MeterCore;

struct MeterHandle {
    MeterCore core;
};

extern "C" {

MeterHandle *meter_create(void) { return new MeterHandle(); }
void meter_destroy(MeterHandle *h) { delete h; }

void meter_configure(MeterHandle *h, double sr) { h->core.configure(sr); }
void meter_process(MeterHandle *h, const float *s, int32_t n) { h->core.process(s, n); }
void meter_on_stopped(MeterHandle *h) { h->core.onStopped(); }

float meter_level_dbfs(const MeterHandle *h) { return h->core.levelDbfs(); }
float meter_max_dbfs(const MeterHandle *h) { return h->core.maxDbfs(); }
bool meter_max_clipped(const MeterHandle *h) { return h->core.maxClipped(); }
float meter_min_dbfs(const MeterHandle *h) { return h->core.minDbfs(); }
float meter_leq_dbfs(const MeterHandle *h) { return h->core.leqDbfs(); }
bool meter_over_range(const MeterHandle *h) { return h->core.overRange(); }

void meter_set_fast(MeterHandle *h, bool fast) { h->core.setFast(fast); }
void meter_set_weighting(MeterHandle *h, int32_t w) { h->core.setWeighting(w); }
void meter_reset_stats(MeterHandle *h) { h->core.resetStats(); }

}  // extern "C"
