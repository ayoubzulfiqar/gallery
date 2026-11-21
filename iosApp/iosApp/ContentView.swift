import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            // Privacy cover: the app-switcher snapshot and any backgrounded frame must never
            // show unlocked vault content (the iOS counterpart of Android's FLAG_SECURE).
            .overlay {
                if scenePhase != .active {
                    Color(red: 0.043, green: 0.059, blue: 0.071)
                        .ignoresSafeArea()
                }
            }
    }
}