import SwiftUI
import Foundation

private enum PullWall: String, CaseIterable, Identifiable {
    case left = "Left", right = "Right", top = "Top", bottom = "Bottom", back = "Back"
    var id: String { rawValue }
}

private enum PullArrangement: String, CaseIterable, Identifiable {
    case straight = "Straight", angle = "Angle", u = "U", mixed = "Mixed", back = "Back wall"
    var id: String { rawValue }
}

private struct Raceway: Identifiable {
    let id = UUID()
    var wall: PullWall
    var trade: Int
}

private enum PullResult {
    case ready(x: Double?, y: Double?, z: Double?, details: [String])
    case invalid(String)
}

struct PullBoxSizingView: View {
    @State private var arrangement: PullArrangement = .straight
    @State private var selectedWall: PullWall = .left
    @State private var trade = 53
    @State private var conduit: [Raceway] = [
        Raceway(wall: .left, trade: 53),
        Raceway(wall: .right, trade: 53)
    ]
    @State private var conductorOD = ""
    @State private var inches = false

    private let sizes = [16, 21, 27, 35, 41, 53, 63, 78, 91, 103, 129, 155]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 7) {
                    Text("PULL BOX · FIELD DIMENSIONS")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    Text("Straight, angle, U, mixed and back-wall entry arrangements.")
                        .font(.subheadline)
                    Text("CEC 2024 Rule 12-3036 study/layout aid for conductors No. 4 AWG or larger. Confirm code, fittings, access and manufacturer clearances.")
                        .font(.caption).foregroundStyle(WorkbenchStyle.muted)
                }
                .pullCard()

                VStack(alignment: .leading, spacing: 12) {
                    Picker("Pull arrangement", selection: $arrangement) {
                        ForEach(PullArrangement.allCases) { item in Text(item.rawValue).tag(item) }
                    }
                    .pickerStyle(.menu)
                    .onChange(of: arrangement) { _, next in
                        switch next {
                        case .straight, .mixed:
                            conduit = [.init(wall: .left, trade: 53), .init(wall: .right, trade: 53)]
                        case .angle:
                            conduit = [.init(wall: .left, trade: 53), .init(wall: .top, trade: 53)]
                        case .u:
                            conduit = [.init(wall: .left, trade: 53), .init(wall: .left, trade: 53)]
                        case .back:
                            conduit = [.init(wall: .back, trade: 53)]
                        }
                    }
                    Toggle("Display dimensions in inches", isOn: $inches)
                        .tint(WorkbenchStyle.cyan)
                    Text("ADD A CONDUIT ENTRY")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    Picker("Entry wall", selection: $selectedWall) {
                        ForEach(PullWall.allCases) { item in Text(item.rawValue).tag(item) }
                    }
                    Picker("Conduit trade size (mm)", selection: $trade) {
                        ForEach(sizes, id: \.self) { size in Text("\(size) mm").tag(size) }
                    }
                    Button {
                        conduit.append(Raceway(wall: selectedWall, trade: trade))
                    } label: {
                        Label("Add raceway", systemImage: "plus.circle")
                            .frame(maxWidth: .infinity, minHeight: 42)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(WorkbenchStyle.cyan)
                    .foregroundStyle(WorkbenchStyle.background)
                    .disabled(conduit.count >= 30)
                }
                .pullCard()

                VStack(alignment: .leading, spacing: 9) {
                    Text("RACEWAYS • \(conduit.count)")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    ForEach(conduit) { item in
                        HStack {
                            Image(systemName: "circle.grid.2x2")
                                .foregroundStyle(WorkbenchStyle.cyan)
                            Text("\(item.wall.rawValue) wall • \(item.trade) mm trade")
                                .font(.subheadline)
                            Spacer()
                            Button(role: .destructive) {
                                conduit.removeAll { $0.id == item.id }
                            } label: {
                                Image(systemName: "trash")
                                    .accessibilityLabel("Remove \(item.wall.rawValue) \(item.trade) mm raceway")
                            }
                            .buttonStyle(.borderless)
                        }
                        .padding(.vertical, 4)
                    }
                    if arrangement == .back || conduit.contains(where: { $0.wall == .back }) {
                        Text("Largest insulated conductor outside diameter, mm")
                            .font(.caption.bold())
                        TextField("Measured OD in mm", text: $conductorOD)
                            .keyboardType(.decimalPad)
                            .padding(10)
                            .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 8))
                    }
                }
                .pullCard()

                VStack(alignment: .leading, spacing: 9) {
                    Text("MINIMUM INSIDE DIMENSIONS")
                        .font(.caption.bold()).foregroundStyle(WorkbenchStyle.cyan)
                    switch result {
                    case let .invalid(reason):
                        Label(reason, systemImage: "exclamationmark.triangle")
                            .foregroundStyle(.orange)
                            .font(.subheadline)
                    case let .ready(x, y, z, notes):
                        dimension("Left ↔ right", x)
                        dimension("Top ↔ bottom", y)
                        dimension("Back → cover", z)
                        Divider()
                        ForEach(notes, id: \.self) { note in
                            Text("• " + note)
                                .font(.caption)
                                .foregroundStyle(WorkbenchStyle.muted)
                        }
                    }
                }
                .pullCard()

                Text("“Not determined” does not mean zero; add all actual raceways and verify bending space, splice volume, wall clearances and nearest-edge separations.")
                    .font(.footnote).foregroundStyle(WorkbenchStyle.muted)

                NavigationLink {
                    PortalScreen(title: "Complete pull-box worksheet", path: "/pull-box-calcs.html")
                } label: {
                    Label("Open detailed web pull-box calculator", systemImage: "square.on.square")
                }
                .font(.subheadline)
            }
            .padding(14)
            .frame(maxWidth: 740)
            .frame(maxWidth: .infinity)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle("Pull-box sizing")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func dimension(_ name: String, _ value: Double?) -> some View {
        HStack {
            Text(name).font(.subheadline)
            Spacer()
            Text(value.map(display) ?? "Not determined")
                .font(.subheadline.bold().monospacedDigit())
        }
    }

    private func display(_ amount: Double) -> String {
        inches
            ? String(format: "%.2f in (%.0f mm)", amount / 25.4, amount)
            : String(format: "%.0f mm (%.2f in)", amount, amount / 25.4)
    }

    private var result: PullResult {
        guard !conduit.isEmpty else { return .invalid("Add at least one raceway.") }
        func entries(_ wall: PullWall) -> [Double] {
            conduit.filter { $0.wall == wall }.map { Double($0.trade) }
        }
        var x: Double? = nil
        var y: Double? = nil
        var z: Double? = nil
        var notes: [String] = []

        func maximum(_ previous: Double?, _ candidate: Double) -> Double {
            max(previous ?? 0, candidate)
        }

        if arrangement == .straight || arrangement == .mixed {
            let horizontal = entries(.left) + entries(.right)
            if !entries(.left).isEmpty && !entries(.right).isEmpty,
               let largest = horizontal.max() {
                x = maximum(x, 8 * largest)
                notes.append("Straight left/right: 8 × \(Int(largest)) mm.")
            }
            let vertical = entries(.top) + entries(.bottom)
            if !entries(.top).isEmpty && !entries(.bottom).isEmpty,
               let largest = vertical.max() {
                y = maximum(y, 8 * largest)
                notes.append("Straight top/bottom: 8 × \(Int(largest)) mm.")
            }
        }
        if [.angle, .u, .mixed].contains(arrangement) {
            for wall in [PullWall.left, .right, .top, .bottom] {
                let sizes = entries(wall)
                guard let largest = sizes.max() else { continue }
                let other = sizes.reduce(0, +) - largest
                let dimension = 6 * largest + other
                if wall == .left || wall == .right {
                    x = maximum(x, dimension)
                } else {
                    y = maximum(y, dimension)
                }
                notes.append("\(wall.rawValue) wall: 6 × \(Int(largest)) + \(Int(other)) mm.")
            }
            if arrangement == .u {
                for wall in [PullWall.left, .right, .top, .bottom] {
                    let sizes = entries(wall)
                    if sizes.count >= 2 {
                        let openingSpan = sizes[0] + sizes[1] + 6 * max(sizes[0], sizes[1])
                        if wall == .top || wall == .bottom {
                            x = maximum(x, openingSpan)
                        } else {
                            y = maximum(y, openingSpan)
                        }
                        notes.append("U-pull nearest-edge opening span applied for \(wall.rawValue).")
                        break
                    }
                }
            }
            notes.append("Entries carrying the same conductor also require nearest-edge spacing checks.")
        }
        if arrangement == .back || !entries(.back).isEmpty {
            guard let largest = entries(.back).max() else {
                return .invalid("Add a raceway on the back wall.")
            }
            guard let diameter = Double(conductorOD), diameter > 0, diameter.isFinite else {
                return .invalid("Measure the insulated conductor OD in mm for back-wall clearance.")
            }
            z = largest + 6 * diameter
            notes.append("Back-to-cover: largest conduit + 6 × insulated conductor OD.")
        }
        return .ready(x: x, y: y, z: z, details: notes)
    }
}

private struct PullCard: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(WorkbenchStyle.border))
    }
}
private extension View {
    func pullCard() -> some View { modifier(PullCard()) }
}
