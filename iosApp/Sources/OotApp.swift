import SwiftUI
import OotApp

@main
struct OcarinaApp: App {
    var body: some Scene {
        WindowGroup {
            GameView().ignoresSafeArea()
        }
    }
}

private struct GameView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.mainViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
