import SwiftUI
import Foundation

/// Field-first iOS circuit review. Works offline, requires user-verified CEC data.
struct UnifiedCircuitSizingView: View {
    @EnvironmentObject private var account: CloudAccountSession
    @State private var project = "Field project"
    @State private var worksheetTitle = "Circuit 1"
    @State private var fieldNotes = ""
    @State private var archiveStatus = "Save a new dated revision after reviewing the circuit."
    @State private var revisions: [CircuitSavedRevision] = []
    @State private var selectedPDFRevision: CircuitSavedRevision?
    @State private var preparedPDF: URL?
    @State private var pdfClient = ""
    @State private var pdfSite = ""
    @State private var pdfReviewer = ""
    @State private var pdfStatus = "Select a saved revision to prepare a PDF calculation report."
    @State private var lastInput: UnifiedCircuitMath.Input?
    @State private var lastResult: UnifiedCircuitMath.Result?
    @State private var lastConductor = ""
    @State private var lastTable = ""
    @State private var loadA = "40"
    @State private var voltageV = "600"
    @State private var phases = 3
    @State private var lengthM = "40"
    @State private var deviceA = "50"
    @State private var multiplier = 1.25
    @State private var conductor = "Selected conductor"
    @State private var table = "Table 2"
    @State private var terminal = 75
    @State private var insulation = 90
    @State private var baseAmpacity = "85"
    @State private var ambient = "1.00"
    @State private var grouping = "1.00"
    @State private var circularMils = "66360"
    @State private var kFactor = "12.9"
    @State private var vdTarget = "3"
    @State private var showVDInputs = false
    @State private var output = "Replace sample entries with verified circuit details."

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("CIRCUIT + CONDUCTOR REVIEW")
                    .font(.caption.bold())
                    .foregroundStyle(WorkbenchStyle.cyan)
                Text("One offline worksheet: required current, corrected ampacity, estimated voltage drop, maximum one-way run and a conservative OCPD screen.")
                    .font(.subheadline)
                    .foregroundStyle(WorkbenchStyle.muted)

                VStack(alignment: .leading, spacing: 12) {
                    Text("1 · Circuit").font(.headline)
                    entry("Actual load current (A)", value: $loadA)
                    entry("System voltage (V)", value: $voltageV)
                    Picker("Circuit type", selection: $phases) {
                        Text("Single-phase two-wire").tag(1)
                        Text("Balanced three-phase").tag(3)
                    }.pickerStyle(.menu)
                    entry("One-way conductor length (m)", value: $lengthM)
                    entry("Selected breaker / fuse (A)", value: $deviceA)
                    Picker("General load-sizing treatment", selection: $multiplier) {
                        Text("100% (non-continuous / approved 100% equipment)").tag(1.0)
                        Text("125% (80%-rated equipment)").tag(1.25)
                    }.pickerStyle(.menu)
                }
                .circuitReviewCard()

                VStack(alignment: .leading, spacing: 12) {
                    Text("2 · Conductor and site conditions").font(.headline)
                    Text("Use the actual equipment TERMINATION-temperature ampacity column, not the conductor's higher insulation column.")
                        .foregroundStyle(.orange)
                        .font(.footnote)
                    entry("Selected conductor / trade size", value: $conductor, numeric: false)
                    Picker("Applicable CEC table", selection: $table) {
                        ForEach(["Table 1", "Table 2", "Table 3", "Table 4"], id: \.self) { value in
                            Text(value).tag(value)
                        }
                    }.pickerStyle(.menu)
                    Picker("Verified termination temperature", selection: $terminal) {
                        ForEach([60,75,90], id: \.self) { value in
                            Text("\(value)°C").tag(value)
                        }
                    }.pickerStyle(.menu)
                    Picker("Insulation temperature", selection: $insulation) {
                        ForEach([60,75,90], id: \.self) { value in
                            Text("\(value)°C").tag(value)
                        }
                    }.pickerStyle(.menu)
                    entry("Ampacity from termination column (A)", value: $baseAmpacity)
                    entry("Verified ambient factor (Table 5A)", value: $ambient)
                    entry("Verified grouping factor (Table 5C)", value: $grouping)
                    Button(showVDInputs ? "Hide voltage-drop inputs" : "Edit voltage-drop inputs") {
                        showVDInputs.toggle()
                    }
                    .buttonStyle(.bordered)
                    if showVDInputs {
                        entry("Conductor area (circular mils)", value: $circularMils)
                        entry("Resistance K factor (Ω·cmil/ft)", value: $kFactor)
                        entry("User-selected voltage-drop target (%)", value: $vdTarget)
                    } else {
                        Text("VD uses sample area \(circularMils) cmil, K \(kFactor), target \(vdTarget)%. Expand to confirm.")
                            .font(.caption)
                            .foregroundStyle(WorkbenchStyle.muted)
                    }
                }
                .circuitReviewCard()

                Button {
                    calculate()
                } label: {
                    Label("Review circuit", systemImage: "function")
                        .frame(maxWidth: .infinity, minHeight: 46)
                }
                .buttonStyle(.borderedProminent)
                .tint(WorkbenchStyle.cyan)
                .foregroundStyle(WorkbenchStyle.background)

                VStack(alignment: .leading, spacing: 10) {
                    Text("3 · Worked result").font(.headline)
                    Text(output)
                        .font(.subheadline.monospacedDigit())
                        .fixedSize(horizontal: false, vertical: true)
                        .textSelection(.enabled)
                    ShareLink(item: "Electrical Workbench · Circuit Review\n" + output) {
                        Label("Share worksheet", systemImage: "square.and.arrow.up")
                    }
                    .font(.subheadline)
                }
                .circuitReviewCard()

                VStack(alignment: .leading, spacing: 11) {
                    Text("4 · Save to project").font(.headline)
                    Text("Each save retains all inputs, numeric results, the worked explanation, source assumptions and the revision date. Previous revisions are not overwritten.")
                        .font(.footnote).foregroundStyle(WorkbenchStyle.muted)
                    entry("Project name", value: $project, numeric: false)
                    entry("Worksheet title", value: $worksheetTitle, numeric: false)
                    entry("Verification / field notes", value: $fieldNotes, numeric: false)
                    Button {
                        saveRevision()
                    } label: {
                        Label("Save new worksheet revision", systemImage: "square.and.arrow.down")
                            .frame(maxWidth: .infinity, minHeight: 44)
                    }
                    .buttonStyle(.borderedProminent)
                    Text(archiveStatus)
                        .font(.footnote).foregroundStyle(.orange)
                    if !revisions.isEmpty {
                        Text("Previous revisions in this project").font(.subheadline.bold())
                        ForEach(revisions.prefix(25)) { rev in
                            Button {
                                openRevision(rev)
                            } label: {
                                VStack(alignment:.leading,spacing:3) {
                                    Text(rev.title + " · revision " + String(rev.revision))
                                        .font(.subheadline.bold())
                                    Text(rev.saved).font(.caption)
                                }
                                .frame(maxWidth:.infinity,alignment:.leading)
                            }
                            .buttonStyle(.bordered)
                        }
                        ShareLink(item:CircuitProjectStore.exportJSON(
                            user:account.username,project:project)) {
                            Label("Share project JSON backup",systemImage:"square.and.arrow.up")
                        }
                        .font(.subheadline)
                        Divider()
                        Text("Saved circuit PDF report").font(.headline)
                        Text("Select a saved revision above. PDF includes its inputs, calculations, assumptions, original results and reviewer/signature section.")
                            .font(.footnote).foregroundStyle(WorkbenchStyle.muted)
                        entry("Client / employer (PDF)",value:$pdfClient,numeric:false)
                        entry("Site / location (PDF)",value:$pdfSite,numeric:false)
                        entry("Reviewer (optional)",value:$pdfReviewer,numeric:false)
                        Button {
                            generatePDF()
                        } label: {
                            Label("Prepare selected revision PDF",systemImage:"doc.richtext")
                                .frame(maxWidth:.infinity,minHeight:44)
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(selectedPDFRevision == nil)
                        if let preparedPDF {
                            ShareLink(item:preparedPDF) {
                                Label("Share / save PDF file",systemImage:"square.and.arrow.up")
                            }
                            .font(.subheadline.bold())
                        }
                        Text(pdfStatus).font(.footnote).foregroundStyle(.orange)
                    }
                }
                .circuitReviewCard()

                Text("NOT CODE VERIFIED. Verify official CEC 2024 / Ontario tables and amendments, actual termination marks, insulation, installation method, neutral counting, current and OCPD rules. The K-factor drop model is approximate and excludes upstream feeder drop; motor and transformer exceptions require separate review. No conductor size is automatically approved.")
                    .font(.footnote)
                    .foregroundStyle(.orange)
            }
            .padding(15)
            .frame(maxWidth: 740)
            .frame(maxWidth: .infinity)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle("Circuit sizing")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { refreshRevisions() }
        .onChange(of: project) { _, _ in refreshRevisions() }
        .onReceive(NotificationCenter.default.publisher(for: .ewbAccountSyncUpdated)) { _ in
            refreshRevisions()
        }
    }

    @ViewBuilder
    private func entry(_ label: String, value: Binding<String>, numeric: Bool = true) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(label).font(.subheadline.weight(.medium))
            TextField("Enter " + label.lowercased(), text: value)
                .keyboardType(numeric ? .decimalPad : .default)
                .padding(11)
                .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 9))
                .accessibilityLabel(label)
        }
    }

    private func finite(_ value: String) -> Double? {
        guard let number = Double(value.trimmingCharacters(in: .whitespaces)),
              number.isFinite else { return nil }
        return number
    }

    private func fmt(_ number: Double) -> String {
        String(format: "%.2f", number)
    }

    private func calculate() {
        lastInput=nil;lastResult=nil
        guard let input=currentInput() else {
            output="Enter finite numeric measurements. Sample values are not verified Code data."
            return
        }
        guard let result=UnifiedCircuitMath.calculate(input) else {
            output="Calculation unavailable. Check positive values, 60/75/90°C terminal and insulation compatibility, correction factors ≤1, and a VD target below 100%."
            return
        }
        lastInput=input;lastResult=result
        lastConductor=conductor;lastTable=table
        let load=input.loadA,base=input.terminalColumnAmpacityA
        let ambientFactor=input.ambientFactor,groupingFactor=input.groupingFactor
        let breaker=input.selectedOcpdA,target=input.maxVoltageDropPercent
        output = """
        \(conductor) · \(table), \(terminal)°C terminations

        1 · CURRENT / AMPACITY
        Design = \(fmt(load)) × \(fmt(multiplier)) = \(fmt(result.designCurrentA)) A
        Adjusted ampacity = \(fmt(base)) × \(fmt(ambientFactor)) × \(fmt(groupingFactor)) = \(fmt(result.adjustedAmpacityA)) A
        Margin = \(fmt(result.conductorMarginA)) A
        Ampacity screen: \(result.ampacityArithmeticFits ? "WITHIN ENTERED LIMIT" : "FAIL")

        2 · VOLTAGE DROP
        Estimated drop = \(fmt(result.voltageDropV)) V (\(fmt(result.voltageDropPercent))%)
        Entered target = \(fmt(result.allowableDropV)) V (\(fmt(target))%)
        VD screen: \(result.voltageDropArithmeticFits ? "WITHIN ENTERED TARGET" : "EXCEEDS TARGET")
        Minimum conductor area for entered VD target = \(fmt(result.minimumCircularMilsForDrop)) circular mils
        Maximum one-way run at selected area = \(fmt(result.maximumOneWayLengthM)) m

        3 · GENERAL-CIRCUIT DEVICE SCREEN
        Entered breaker/fuse = \(fmt(breaker)) A
        Conservative screen: \(result.protectionScreenFits ? "WITHIN INPUT BOUNDS" : "REVIEW DEVICE / LOAD / CONDUCTOR")

        NOT CODE VERIFIED. No automatic conductor/OCPD approval. Confirm verified tables, terminal markings and actual wiring method. Voltage drop is a resistive K-factor estimate.
        """
    }
    private func currentInput() -> UnifiedCircuitMath.Input? {
        guard let load=finite(loadA),let volts=finite(voltageV),
              let distance=finite(lengthM),let breaker=finite(deviceA),
              let base=finite(baseAmpacity),let ambientFactor=finite(ambient),
              let groupingFactor=finite(grouping),let area=finite(circularMils),
              let k=finite(kFactor),let target=finite(vdTarget) else {return nil}
        return UnifiedCircuitMath.Input(
            loadA:load,designMultiplier:multiplier,voltageV:volts,
            phases:phases,oneWayLengthM:distance,
            circularMils:area,kOhmCircularMilPerFoot:k,
            terminalColumnAmpacityA:base,terminalC:terminal,
            insulationC:insulation,ambientFactor:ambientFactor,
            groupingFactor:groupingFactor,selectedOcpdA:breaker,
            maxVoltageDropPercent:target
        )
    }

    private func refreshRevisions() {
        selectedPDFRevision=nil
        preparedPDF=nil
        revisions=CircuitProjectStore.rows(user:account.username,
            project:project.trimmingCharacters(in:.whitespacesAndNewlines))
    }

    private func saveRevision() {
        guard let input=currentInput(),let calculated=lastInput,
              let outputValues=lastResult,input==calculated,
              conductor==lastConductor,table==lastTable else {
            archiveStatus="Calculate the current fields before saving; a previous result may be stale."
            return
        }
        do {
            let number=try CircuitProjectStore.save(
                user:account.username,project:project,title:worksheetTitle,
                notes:fieldNotes,conductor:conductor,table:table,
                input:input,result:outputValues,report:output)
            refreshRevisions()
            archiveStatus="Saved revision \(number) locally. Account synchronization runs when connected."
        }catch {
            archiveStatus="Not saved: "+error.localizedDescription
        }
    }

    private func generatePDF() {
        guard let revision=selectedPDFRevision else {
            pdfStatus="Select a saved revision before exporting."
            return
        }
        do {
            preparedPDF=try CircuitProjectPDF.prepare(
                record:revision.raw,client:pdfClient,site:pdfSite,reviewer:pdfReviewer)
            pdfStatus="PDF created for "+revision.title+" · revision "+String(revision.revision)+
                ". Select Share / save PDF to export the file."
        } catch {
            preparedPDF=nil
            pdfStatus="PDF could not be generated: "+error.localizedDescription
        }
    }

    private func openRevision(_ record:CircuitSavedRevision) {
        guard let data=record.raw["inputs"] as? [String:Any] else {
            archiveStatus="Saved revision is missing its input fields."
            return
        }
        func num(_ key:String,_ fallback:String)->String {
            (data[key] as? NSNumber)?.stringValue ?? fallback
        }
        selectedPDFRevision=record
        preparedPDF=nil
        pdfStatus="Selected "+record.title+" · revision "+String(record.revision)+
            " for PDF export."
        loadA=num("loadA",loadA);voltageV=num("voltageV",voltageV)
        phases=(data["phases"] as? NSNumber)?.intValue ?? phases
        lengthM=num("oneWayLengthM",lengthM)
        deviceA=num("selectedOcpdA",deviceA)
        multiplier=(data["designMultiplier"] as? NSNumber)?.doubleValue ?? multiplier
        conductor=data["conductorDescription"] as? String ?? conductor
        table=data["tableReference"] as? String ?? table
        terminal=(data["terminalC"] as? NSNumber)?.intValue ?? terminal
        insulation=(data["insulationC"] as? NSNumber)?.intValue ?? insulation
        baseAmpacity=num("terminalColumnAmpacityA",baseAmpacity)
        ambient=num("ambientFactor",ambient);grouping=num("groupingFactor",grouping)
        circularMils=num("circularMils",circularMils)
        kFactor=num("kOhmCircularMilPerFoot",kFactor)
        vdTarget=num("maxVoltageDropPercent",vdTarget)
        project=record.project;worksheetTitle=record.title
        fieldNotes=(record.raw["assumptions"] as? [String:Any])?["notes"] as? String ?? ""
        output=(record.raw["result"] as? String ?? "") + "\n\nOpened archived revision. Recalculate to save any changes."
        lastInput=nil;lastResult=nil
        archiveStatus="Loaded \(record.title) · revision \(record.revision) · \(record.saved) (NOT CODE VERIFIED)"
    }

}

private struct CircuitReviewCard: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 14))
    }
}

private extension View {
    func circuitReviewCard() -> some View { modifier(CircuitReviewCard()) }
}
