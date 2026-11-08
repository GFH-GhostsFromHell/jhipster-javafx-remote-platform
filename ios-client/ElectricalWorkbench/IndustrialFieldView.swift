import SwiftUI

/// Three-input, one-screen offline industrial arithmetic for field measurements.
struct IndustrialFieldView: View {
    @State private var selected:IndustrialWorksheet = .ct
    @State private var entries:[String:String] = [:]
    @State private var output="Choose a calculation and replace all example measurements."
    @State private var status="Example data; not verified."

    var body:some View {
        ScrollView {
            VStack(alignment:.leading,spacing:14){
                Text("INDUSTRIAL FIELD WORKSHEETS")
                    .font(.caption.bold())
                    .foregroundStyle(WorkbenchStyle.cyan)
                Text("CT/PT transformation, balanced power, relay pickup translation, ideal transformer short circuit and 4–20 mA scaling.")
                    .font(.subheadline).foregroundStyle(WorkbenchStyle.muted)

                VStack(alignment:.leading,spacing:12){
                    Text("1 · Choose worksheet").font(.headline)
                    Picker("Calculation",selection:$selected){
                        ForEach(IndustrialWorksheet.allCases){mode in
                            Text(mode.title).tag(mode)
                        }
                    }
                    .pickerStyle(.menu)
                }
                .industrialCard()

                VStack(alignment:.leading,spacing:10){
                    Text("2 · Enter nameplate and field values").font(.headline)
                    Text("Illustrative values only. Confirm actual equipment markings and measurements.")
                        .font(.footnote).foregroundStyle(.orange)
                    ForEach(selected.fields){field in
                        VStack(alignment:.leading,spacing:4){
                            Text(field.label).font(.subheadline.weight(.medium))
                            TextField(field.example,text:Binding(
                                get:{entries[field.id] ?? field.example},
                                set:{entries[field.id]=$0}
                            ))
                            .keyboardType(.numbersAndPunctuation)
                            .padding(12)
                            .background(WorkbenchStyle.background,
                                        in:RoundedRectangle(cornerRadius:9))
                            .accessibilityLabel(field.label)
                        }
                    }
                }
                .industrialCard()

                Button(action:calculate){
                    Label("Calculate",systemImage:"function")
                        .frame(maxWidth:.infinity,minHeight:46)
                }
                .buttonStyle(.borderedProminent)
                .tint(WorkbenchStyle.cyan)

                VStack(alignment:.leading,spacing:10){
                    Text("3 · Calculation result").font(.headline)
                    Text(status).font(.footnote).foregroundStyle(.orange)
                    Text(output).font(.subheadline.monospacedDigit())
                        .fixedSize(horizontal:false,vertical:true)
                        .textSelection(.enabled)
                    ShareLink(item:"Electrical Workbench · Industrial Field Review\n"+output){
                        Label("Share arithmetic worksheet",systemImage:"square.and.arrow.up")
                    }.font(.subheadline)
                }
                .industrialCard()

                Text("NOT CODE VERIFIED. No relay protection setting, available fault-current rating, arc-flash study or electrical installation is approved by this arithmetic worksheet.")
                    .font(.footnote).foregroundStyle(.orange)
            }
            .padding(15)
            .frame(maxWidth:760)
            .frame(maxWidth:.infinity)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle("Industrial field")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform:reset)
        .onChange(of:selected){ _,_ in reset() }
    }

    private func reset(){
        entries=Dictionary(uniqueKeysWithValues:selected.fields.map{($0.id,$0.example)})
        output="No calculation performed. Verify all example numbers."
        status="Sample measurements; replace for field use."
    }

    private func calculate(){
        var inputs:[String:Double]=[:]
        for field in selected.fields{
            let raw=entries[field.id]?.trimmingCharacters(in:.whitespaces) ?? ""
            guard !raw.isEmpty,let n=Double(raw),n.isFinite else {
                status="Cannot calculate; check the entered measurements."
                output="Enter a finite measurement for "+field.label+"."
                return
            }
            inputs[field.id]=n
        }
        do {
            let result=try IndustrialFieldMath.calculate(selected,values:inputs)
            let lines=result.readings.map{
                "\($0.0): "+String(format:"%.4f",$0.1)
            }
            output=([selected.title,""]+lines+["",
                "METHOD LIMITATIONS",result.caution,"",
                "Reference profile: OESC_ON_2024",
                "Numeric Code table source: verification required.",
                "NOT CODE VERIFIED / NO RELAY SETTING APPROVED."
            ]).joined(separator:"\n")
            status="Arithmetic calculated. Verify independently."
        }catch{
            status="Cannot calculate; correct the field inputs."
            output=error.localizedDescription
        }
    }
}

private struct IndustrialFieldCard:ViewModifier {
    func body(content:Content)->some View {
        content.padding(14)
            .frame(maxWidth:.infinity,alignment:.leading)
            .background(WorkbenchStyle.surface,in:RoundedRectangle(cornerRadius:14))
    }
}
private extension View {
    func industrialCard()->some View{ modifier(IndustrialFieldCard()) }
}
