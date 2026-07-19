import SwiftUI
import CoreText

@main
struct HowlerMeterApp: App {
    init() { Self.registerBundledFonts() }

    var body: some Scene {
        WindowGroup { ContentView() }
    }

    /// Register DSEG7 at launch instead of via an Info.plist `UIAppFonts` array —
    /// the target uses a generated Info.plist (`GENERATE_INFOPLIST_FILE=YES`) that
    /// can't carry the font list as a build setting, so we register from the bundle.
    private static func registerBundledFonts() {
        guard let url = Bundle.main.url(forResource: "dseg7_classic_bold", withExtension: "ttf") else {
            assertionFailure("DSEG7 font missing from bundle — hero readout falls back to system mono")
            return
        }
        let ok = CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        assert(ok, "DSEG7 font failed to register — hero readout falls back to system mono")
    }
}
