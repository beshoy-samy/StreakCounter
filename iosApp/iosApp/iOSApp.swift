import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        // Once per process, before any view controller exists.
        InitKoinKt.doInitKoin(config: nil)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea()
        }
    }
}
