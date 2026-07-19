import SwiftUI

/// CRT amber-phosphor palette — iOS mirror of the Android `Phosphor` object
/// (`ui/theme/Color.kt`). Literal values from the design comp
/// (design/howler-ui-spec.md §1); keep the two in sync.
enum Phosphor {
    static let screen = Color(hex: 0x080400)              // screen background
    static let readout = Color(hex: 0xFFC23A)             // readout / active text
    static let label = Color(hex: 0x9A6F22)              // MAX/MIN/Leq labels
    static let labelBright = Color(hex: 0xC98A2E)
    static let caption = Color(hex: 0x8A6320)            // calibration caption
    static let toggleActiveFill = Color(hex: 0x3A2600)
    static let toggleActiveBorder = Color(hex: 0xFFB02E)
    static let toggleInactiveBorder = Color(hex: 0x6E4A0F)
    static let toggleInactiveText = Color(hex: 0xA9781F)
    static let glow = Color(hex: 0xFF961C)               // reactive edge glow
    static let overBg = Color(hex: 0x3A0E06)             // OVER / danger
    static let overBorder = Color(hex: 0xFF4326)
    static let overText = Color(hex: 0xFF5B3A)
    static let scanline = Color.black                     // overlay, ~0.42 alpha
}

extension Color {
    /// 0xRRGGBB literal → Color (opaque). Matches the Android ARGB constants
    /// with the alpha byte dropped (all Phosphor colors are fully opaque).
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}
