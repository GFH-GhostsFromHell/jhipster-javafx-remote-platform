import Foundation

/// Foundation-only formatter for a saved circuit revision. Portable to
/// simulator self-tests; output is explicitly NOT a Code approval.
enum CircuitProjectReportText {
    static func build(_ record: [String:Any],
                      client: String = "",site: String = "",reviewer: String = "") throws -> String {
        guard record["kind"] as? String == "circuitWorksheet",
              let input=record["inputs"] as? [String:Any],
              let result=record["results"] as? [String:Any],
              (result["codeComplianceVerified"] as? Bool) == false else {
            throw ReportError.invalidRecord
        }
        func value(_ dictionary:[String:Any],_ key:String) -> String {
            guard let raw=dictionary[key] else { return "Not supplied" }
            if let text=raw as? String { return text }
            return String(describing:raw)
        }
        var lines:[String]=[]
        func heading(_ text:String){lines.append("");lines.append(text.uppercased())}
        func entry(_ title:String,_ dictionary:[String:Any],_ key:String,_ unit:String="") {
            lines.append(title+": "+value(dictionary,key)+unit)
        }
        lines.append("ELECTRICAL WORKBENCH - CIRCUIT CALCULATION REPORT")
        lines.append("PRELIMINARY - NOT CODE VERIFIED - NOT FOR INSTALLATION APPROVAL")
        heading("Project and revision")
        entry("Project",record,"project")
        entry("Worksheet",record,"title")
        entry("Revision",record,"revision")
        entry("Saved UTC",record,"saved")
        lines.append("Record ID: "+(record["_syncId"] as? String ??
                                   record["id"] as? String ?? "Not supplied"))
        lines.append("PDF exported UTC: "+ISO8601DateFormatter().string(from:Date()))
        lines.append("Client / employer: "+(client.isEmpty ? "Not supplied":client))
        lines.append("Site / location: "+(site.isEmpty ? "Not supplied":site))
        heading("Code reference and verification")
        lines.append("Reference profile: OESC_ON_2024 / CSA C22.1:24")
        lines.append("Numeric source status: AUTHORIZED_TABLE_VERIFICATION_REQUIRED")
        entry("Edition",record,"codeEdition")
        entry("Jurisdiction",record,"jurisdiction")
        entry("Entered tables",record,"referenceTables")
        lines.append("Equipment terminal markings verified: NO")
        lines.append("Code compliance verified: NO")
        heading("Entered electrical circuit and conductor inputs")
        entry("Actual load",input,"loadA"," A")
        entry("Design multiplier",input,"designMultiplier")
        entry("Supply voltage",input,"voltageV"," V")
        entry("Phases",input,"phases")
        entry("One-way length",input,"oneWayLengthM"," m")
        entry("Conductor",input,"conductorDescription")
        entry("Ampacity table",input,"tableReference")
        entry("Terminal temperature",input,"terminalC"," C")
        entry("Insulation temperature",input,"insulationC"," C")
        entry("Base termination-column ampacity",input,"terminalColumnAmpacityA"," A")
        entry("Ambient correction",input,"ambientFactor")
        entry("Grouping correction",input,"groupingFactor")
        entry("Selected conductor circular mils",input,"circularMils")
        entry("Resistance K",input,"kOhmCircularMilPerFoot")
        entry("VD target",input,"maxVoltageDropPercent"," %")
        entry("Entered OCPD",input,"selectedOcpdA"," A")
        heading("Calculated arithmetic results")
        entry("Required design current",result,"designCurrentA"," A")
        entry("Adjusted conductor ampacity",result,"adjustedAmpacityA"," A")
        entry("Conductor margin",result,"conductorMarginA"," A")
        entry("Calculated voltage drop",result,"voltageDropV"," V")
        entry("Calculated voltage drop",result,"voltageDropPercent"," %")
        entry("Entered allowable drop",result,"allowableDropV"," V")
        entry("Minimum circular mils for VD",result,"minimumCircularMilsForDrop")
        entry("Maximum one-way length",result,"maximumOneWayLengthM"," m")
        lines.append("Ampacity arithmetic: "+((result["ampacityArithmeticFits"] as? Bool)==true ?
             "WITHIN INPUT LIMIT":"REVIEW / FAIL"))
        lines.append("Voltage drop arithmetic: "+((result["voltageDropArithmeticFits"] as? Bool)==true ?
             "WITHIN ENTERED TARGET":"REVIEW / FAIL"))
        lines.append("General OCPD arithmetic: "+((result["protectionScreenFits"] as? Bool)==true ?
             "WITHIN INPUT BOUNDS":"REVIEW"))
        heading("Field notes / installation assumptions")
        let assumptions=record["assumptions"] as? [String:Any] ?? [:]
        entry("Notes",assumptions,"notes")
        lines.append("Resistive K-factor approximation, not full AC impedance.")
        lines.append("Upstream voltage drop and special circuit rules not verified.")
        heading("Original saved calculation")
        lines.append(record["result"] as? String ?? "Not supplied")
        heading("Reviewer and signature")
        lines.append("Reviewed by: "+(reviewer.isEmpty ? "______________________":reviewer))
        lines.append("Review date: _______________________")
        lines.append("Signature: _________________________")
        lines.append("No electrical installation approval or engineering seal provided.")
        heading("Mandatory field verification notice")
        lines.append("Verify official CEC/OESC tables and Ontario amendments, terminal")
        lines.append("markings, conductor type, wiring method, neutral counting,")
        lines.append("corrections, protection and total voltage drop independently.")
        return lines.joined(separator:"\n")
    }

    enum ReportError: LocalizedError {
        case invalidRecord
        var errorDescription:String? {
            "Choose a saved, unapproved circuit revision containing inputs and results."
        }
    }
}
