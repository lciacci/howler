import SwiftUI

// ponytail: placeholder debug UI — FOUNDATION ONLY. The real CRT amber-phosphor /
// DSEG7 front end is ADR-0001 step 3, deliberately held until Android closed-
// testing feedback lands so it's ported once against a validated design. This view
// exists so the target builds, runs, and proves the bridge is wired end to end.
struct ContentView: View {
    @StateObject private var meter = MeterEngine()

    var body: some View {
        VStack(spacing: 20) {
            let spl = meter.splFromDbfs(meter.levelDbfs)
            Text(String(format: "%.1f", spl ?? meter.levelDbfs))
                .font(.system(size: 56, weight: .bold, design: .monospaced))
            Text(spl != nil ? "dB SPL" : "dBFS (uncalibrated)")
                .font(.caption).foregroundStyle(.secondary)

            Text(String(format: "max %.1f   min %.1f   Leq %.1f",
                        meter.splFromDbfs(meter.maxDbfs) ?? meter.maxDbfs,
                        meter.splFromDbfs(meter.minDbfs) ?? meter.minDbfs,
                        meter.splFromDbfs(meter.leqDbfs) ?? meter.leqDbfs))
                .font(.footnote.monospaced())
            if meter.over { Text("OVER").font(.headline).foregroundStyle(.red) }

            Button(meter.running ? "Stop" : "Start") {
                if meter.running { meter.stop() } else { try? meter.start() }
            }
            .font(.title2).buttonStyle(.borderedProminent)
        }
        .padding()
    }
}

#Preview { ContentView() }
