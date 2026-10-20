import Shared
import SwiftUI

@main
struct iOSApp: App {
    init() {
        IosScreenShare.shared.broadcast = ScreenShareBroadcast()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}