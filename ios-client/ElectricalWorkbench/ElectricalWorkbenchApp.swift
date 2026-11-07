import SwiftUI

@main
struct ElectricalWorkbenchApp: App {
    var body: some Scene {
        WindowGroup {
            WorkbenchRoot()
                .tint(WorkbenchStyle.cyan)
                .preferredColorScheme(.dark)
        }
    }
}

enum WorkbenchStyle {
    static let background = Color(red: 0.035, green: 0.075, blue: 0.125)
    static let surface = Color(red: 0.065, green: 0.155, blue: 0.24)
    static let border = Color(red: 0.20, green: 0.37, blue: 0.47)
    static let cyan = Color(red: 0.32, green: 0.87, blue: 0.93)
    static let muted = Color(red: 0.73, green: 0.84, blue: 0.90)
}
