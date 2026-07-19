import SwiftUI

// CRT amber-phosphor / DSEG7 front end — iOS port of the Android `MeterScreen`
// (MainActivity.kt). Same layout, states, and phosphor styling; binds to the
// shared MeterEngine (which drives the shared C++ DSP). ADR-0001 step 3.

/// DSEG7 Classic Bold, registered at launch (HowlerMeterApp). `fixedSize` keeps the
/// hero readout from being resized by Dynamic Type.
private func dseg(_ size: CGFloat) -> Font { .custom("DSEG7Classic-Bold", fixedSize: size) }
private func mono(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
    .system(size: size, weight: weight, design: .monospaced)
}

struct ContentView: View {
    @StateObject private var meter = MeterEngine()
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL
    @AppStorage("first_run_seen") private var firstRunSeen = false
    @State private var showFirstRun = false
    @State private var showCalibrate = false

    var body: some View {
        ZStack {
            Phosphor.screen.ignoresSafeArea()
            content
            ScanlineOverlay().allowsHitTesting(false).ignoresSafeArea()
            if showFirstRun { firstRunDialog }
            if showCalibrate { calibrateDialog }
        }
        .onAppear {
            showFirstRun = !firstRunSeen
            meter.requestPermission()
        }
        .onChange(of: meter.micGranted) { _, granted in
            if granted == true && !meter.running { try? meter.start() }
            // Revoked mid-session (Settings) — halt so we don't display stale data.
            // ponytail: iOS usually terminates the app on a permission change, so this
            // rarely fires; the stop is the cheap correct backstop when it does.
            else if granted == false { meter.stop() }
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active: if meter.micGranted == true && !meter.running { try? meter.start() }
            case .background: meter.stop()
            default: break
            }
        }
    }

    // MARK: top-level state routing (mirrors MainActivity's granted / started gates)

    @ViewBuilder private var content: some View {
        switch meter.micGranted {
        case .some(false): centered("MIC PERMISSION REQUIRED")
        case .none: Color.clear                     // waiting on the system prompt
        case .some(true):
            if meter.running { meterScreen }
            else { InputUnavailable { try? meter.start() } }
        }
    }

    // MARK: the live meter

    private var meterScreen: some View {
        let spl = meter.splFromDbfs(meter.levelDbfs)
        let displayLevel = spl ?? meter.levelDbfs
        let wLetter = ["Z", "A", "C"][Int(meter.weighting)]
        return ZStack {
            HeadAndGlow(glowT: smoothstep(50, 88, displayLevel))
            VStack {
                topRow
                Spacer()
                readoutBlock(displayLevel, wLetter)
                Spacer()
                bottomBlock(wLetter)
            }
            .padding(20)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private var topRow: some View {
        HStack {
            Segmented(items: [("A", Int32(1)), ("C", 2), ("Z", 0)],
                      selected: meter.weighting) { meter.weighting = $0 }
            Spacer()
            HStack(spacing: 14) {
                OverIndicator(lit: meter.over)
                Text("?").font(mono(20)).foregroundColor(Phosphor.caption)
                    .onTapGesture { showFirstRun = true }
            }
        }
    }

    private func readoutBlock(_ level: Float, _ wLetter: String) -> some View {
        let bigText = String(format: "%.1f", level)
        // DSEG glyphs are wide — shrink for longer strings so 3-digit SPL (or a
        // negative dBFS) never clips; 2-digit readings stay huge.
        let bigSize: CGFloat = bigText.count <= 4 ? 108 : (bigText.count == 5 ? 84 : 66)
        return VStack(spacing: 4) {
            Text(bigText).font(dseg(bigSize)).foregroundColor(Phosphor.readout).lineLimit(1)
            Text("dB(\(wLetter)) · \(meter.fast ? "fast" : "slow")")
                .font(mono(15)).foregroundColor(Phosphor.labelBright)
        }
    }

    private func bottomBlock(_ wLetter: String) -> some View {
        VStack(spacing: 14) {
            Segmented(items: [("FAST", 1), ("SLOW", 0)],
                      selected: meter.fast ? 1 : 0) { meter.fast = ($0 == 1) }
            if meter.maxDbfs > -160 {
                StatsRow(maxDbfs: meter.maxDbfs, maxClipped: meter.maxClipped,
                         minDbfs: meter.minDbfs, leqDbfs: meter.leqDbfs, spl: meter.splFromDbfs)
                Button { meter.resetStats() } label: {
                    Text("RESET MAX / MIN / Leq").font(mono(12)).foregroundColor(Phosphor.toggleInactiveText)
                }
            }
            Text(calibrationCaption(meter.calibration, wLetter))
                .font(mono(12)).foregroundColor(Phosphor.caption)
                .multilineTextAlignment(.center)
                .onTapGesture { showCalibrate = true }
        }
    }

    // MARK: dialogs

    private var firstRunDialog: some View {
        PhosphorCard {
            Text("BEFORE YOU MEASURE").font(mono(17, .bold)).foregroundColor(Phosphor.readout)
            Text("Accuracy depends on your device's microphone. Without calibration, readings reflect relative changes reliably — but absolute levels can be 10–15 dB off.")
                .font(mono(13)).foregroundColor(Phosphor.labelBright)
            Text("Tap the label at the bottom of the screen to calibrate against a reference source.")
                .font(mono(13)).foregroundColor(Phosphor.labelBright)
            HStack {
                Button("LEARN MORE") {
                    dismissFirstRun()
                    openURL(URL(string: "https://houseofyeti.com/howler/")!)
                }.font(mono(14)).foregroundColor(Phosphor.caption)
                Spacer()
                Button("GOT IT") { dismissFirstRun() }
                    .font(mono(14)).foregroundColor(Phosphor.readout)
            }
        }
    }

    private func dismissFirstRun() { firstRunSeen = true; showFirstRun = false }

    private var calibrateDialog: some View {
        CalibrateDialog(
            currentDbfs: meter.levelDbfs,
            onSave: { refSpl, meterClass in
                meter.saveManualCalibration(referenceSpl: refSpl, meterClass: meterClass)
                showCalibrate = false
            },
            onClear: { meter.clearCalibration(); showCalibrate = false },
            onDismiss: { showCalibrate = false }
        )
    }

    private func centered(_ text: String) -> some View {
        Text(text).font(mono(15)).foregroundColor(Phosphor.labelBright)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Head + reactive glow (ported layer-for-layer from Android HeadAndGlow)

private struct HeadAndGlow: View {
    let glowT: Float
    private let ratio: CGFloat = 505.0 / 380.0   // height / width of the asset

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width * 0.66
            let h = w * ratio
            let glowAlpha = Double(glowT) * 0.4
            ZStack {
                // 1. blurred amber silhouette — spread grows with level.
                if glowAlpha > 0.001 {
                    head(.template, w, h).foregroundColor(Phosphor.glow)
                        .blur(radius: lerp(14, 40, glowT)).opacity(glowAlpha)
                }
                // 2. opaque black mask — blocks the glow from the head's interior.
                head(.template, w, h).foregroundColor(.black)
                // 3. dim phosphor face over the mask (original amber art).
                head(.original, w, h).opacity(0.33)
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }

    private func head(_ mode: Image.TemplateRenderingMode, _ w: CGFloat, _ h: CGFloat) -> some View {
        Image("howler_head").renderingMode(mode).resizable().frame(width: w, height: h)
    }
}

// MARK: - Controls

/// Segmented control: one active cell, brighter than the rest (not greyed).
private struct Segmented<T: Equatable>: View {
    let items: [(String, T)]
    let selected: T
    let onSelect: (T) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(items.indices, id: \.self) { i in
                let (label, value) = items[i]
                let active = value == selected
                Text(label).font(mono(14))
                    .foregroundColor(active ? Phosphor.readout : Phosphor.toggleInactiveText)
                    .padding(.horizontal, 16).padding(.vertical, 7)
                    .background(active ? Phosphor.toggleActiveFill : Color.clear)
                    .overlay(Rectangle().stroke(
                        active ? Phosphor.toggleActiveBorder : Phosphor.toggleInactiveBorder, lineWidth: 1))
                    .onTapGesture { onSelect(value) }
            }
        }
    }
}

/// The lone red: lit on clip / over-range, dim outline otherwise.
private struct OverIndicator: View {
    let lit: Bool
    var body: some View {
        Text("OVER").font(mono(14, .bold))
            .foregroundColor(lit ? Phosphor.overText : Phosphor.toggleInactiveBorder)
            .padding(.horizontal, 16).padding(.vertical, 7)
            .background(lit ? Phosphor.overBg : Color.clear)
            .overlay(RoundedRectangle(cornerRadius: 3).stroke(
                lit ? Phosphor.overBorder : Phosphor.toggleInactiveBorder, lineWidth: 1))
    }
}

/// MAX / MIN / Leq — dim labels over amber values. The Max value carries a red
/// "≥" when the peak came from a clipped block (true level is higher).
private struct StatsRow: View {
    let maxDbfs, minDbfs, leqDbfs: Float
    let maxClipped: Bool
    let spl: (Float) -> Float?

    init(maxDbfs: Float, maxClipped: Bool, minDbfs: Float, leqDbfs: Float, spl: @escaping (Float) -> Float?) {
        self.maxDbfs = maxDbfs; self.maxClipped = maxClipped
        self.minDbfs = minDbfs; self.leqDbfs = leqDbfs; self.spl = spl
    }

    var body: some View {
        HStack(spacing: 28) {
            cell("MAX", maxDbfs, clipped: maxClipped)
            cell("MIN", minDbfs)
            cell("Leq", leqDbfs)
        }
    }

    private func cell(_ label: String, _ dbfs: Float, clipped: Bool = false) -> some View {
        VStack(spacing: 2) {
            Text(label).font(mono(11)).foregroundColor(Phosphor.label)
            HStack(spacing: 2) {
                if clipped { Text("≥").font(mono(18)).foregroundColor(Phosphor.overText) }
                Text(String(format: "%.1f", spl(dbfs) ?? dbfs)).font(mono(18)).foregroundColor(Phosphor.readout)
            }
        }
    }
}

/// Recoverable "mic unavailable" state — another app likely holds the input.
private struct InputUnavailable: View {
    let onRetry: () -> Void
    var body: some View {
        VStack(spacing: 16) {
            Text("INPUT UNAVAILABLE").font(mono(18, .bold)).foregroundColor(Phosphor.overText)
            Text("the mic is held by another app").font(mono(13))
                .foregroundColor(Phosphor.labelBright).multilineTextAlignment(.center)
            Text("RETRY").font(mono(15)).foregroundColor(Phosphor.readout)
                .padding(.horizontal, 24).padding(.vertical, 10)
                .background(Phosphor.toggleActiveFill)
                .overlay(Rectangle().stroke(Phosphor.toggleActiveBorder, lineWidth: 1))
                .onTapGesture(perform: onRetry)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity).padding(24)
    }
}

/// Subtle CRT scanlines over everything — 3pt pitch, low alpha, non-interactive.
private struct ScanlineOverlay: View {
    var body: some View {
        Canvas { ctx, size in
            let pitch: CGFloat = 3, stroke: CGFloat = 1
            let color = GraphicsContext.Shading.color(Phosphor.scanline.opacity(0.42))
            var y: CGFloat = 0
            while y < size.height {
                var p = Path(); p.move(to: CGPoint(x: 0, y: y)); p.addLine(to: CGPoint(x: size.width, y: y))
                ctx.stroke(p, with: color, lineWidth: stroke)
                y += pitch
            }
        }
    }
}

/// Phosphor-themed modal card over a dimmed backdrop — the CRT analog of Android's
/// themed AlertDialog (SwiftUI `.alert` can't carry the palette).
private struct PhosphorCard<Content: View>: View {
    @ViewBuilder let content: Content
    var body: some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 12) { content }
                .padding(24)
                .background(Phosphor.screen)
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(Phosphor.toggleInactiveBorder, lineWidth: 1))
                .padding(32)
        }
    }
}

private struct CalibrateDialog: View {
    let currentDbfs: Float
    let onSave: (Double, String) -> Void
    let onClear: () -> Void
    let onDismiss: () -> Void

    @State private var refSplText = ""
    @State private var meterText = "class-2"
    private var refSpl: Double? { Double(refSplText) }

    var body: some View {
        PhosphorCard {
            Text("MANUAL CALIBRATION").font(mono(17, .bold)).foregroundColor(Phosphor.readout)
            Text("Hold your reference meter at a steady source — best near 1 kHz — then enter its reading.")
                .font(mono(13)).foregroundColor(Phosphor.labelBright)
            Text(String(format: "phone now: %.1f dBFS", currentDbfs))
                .font(mono(13)).foregroundColor(Phosphor.caption)
            field("reference dB SPL", text: $refSplText, decimal: true)
            field("reference meter class", text: $meterText, decimal: false)
            HStack {
                Button("CLEAR", action: onClear).font(mono(14)).foregroundColor(Phosphor.overText)
                Spacer()
                Button("CANCEL", action: onDismiss).font(mono(14)).foregroundColor(Phosphor.toggleInactiveText)
                Button("SAVE") { if let r = refSpl { onSave(r, meterText) } }
                    .font(mono(14)).foregroundColor(refSpl != nil ? Phosphor.readout : Phosphor.toggleInactiveBorder)
                    .disabled(refSpl == nil)
                    .padding(.leading, 16)
            }
        }
    }

    private func field(_ label: String, text: Binding<String>, decimal: Bool) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label).font(mono(11)).foregroundColor(Phosphor.toggleInactiveText)
            TextField("", text: text)
                .font(mono(15)).foregroundColor(Phosphor.readout)
                .keyboardType(decimal ? .decimalPad : .default)
                .padding(8)
                .overlay(Rectangle().stroke(Phosphor.toggleInactiveBorder, lineWidth: 1))
        }
    }
}

// MARK: - pure helpers (mirror MainActivity's private fns)

/// Hermite smoothstep — 0 below edge0, 1 above edge1, eased between.
private func smoothstep(_ edge0: Float, _ edge1: Float, _ x: Float) -> Float {
    let t = min(max((x - edge0) / (edge1 - edge0), 0), 1)
    return t * t * (3 - 2 * t)
}

private func lerp(_ a: CGFloat, _ b: CGFloat, _ t: Float) -> CGFloat { a + (b - a) * CGFloat(t) }

/// Bottom calibration caption: accuracy claim + source, or uncalibrated.
private func calibrationCaption(_ cal: Calibration, _ w: String) -> String {
    switch cal {
    case .uncalibrated: return "uncalibrated · relative dBFS · tap to calibrate"
    case .manual(_, let cls): return "≈ ±2 dB(\(w)) · tier 2 · \(cls)"
    }
}

#Preview { ContentView() }
