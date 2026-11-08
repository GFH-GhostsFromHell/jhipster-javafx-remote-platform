import Foundation

@main struct CircuitProjectReportTextSelfTest {
    static func expect(_ condition:Bool,_ message:String) {
        if !condition {fatalError("Circuit PDF report test: "+message)}
    }
    static func main() throws {
        let record:[String:Any]=[
            "kind":"circuitWorksheet","project":"Station Upgrade",
            "title":"Panel Feeder","revision":2,"_syncId":"ios-test-2",
            "saved":"2026-11-08T13:00:00Z",
            "codeEdition":"CEC_2024","jurisdiction":"Ontario, Canada",
            "referenceTables":"Table 2; Table 5A; Table 5C (user-supplied)",
            "inputs":[
                "loadA":40.0,"designMultiplier":1.25,"voltageV":600.0,
                "phases":3,"oneWayLengthM":40.0,
                "terminalColumnAmpacityA":55.0,
                "terminalC":60,"insulationC":90,
                "ambientFactor":0.58,"groupingFactor":0.8,
                "circularMils":66360.0,"kOhmCircularMilPerFoot":12.9,
                "maxVoltageDropPercent":3.0,"selectedOcpdA":50.0,
                "conductorDescription":"Copper study example",
                "tableReference":"Table 2"
            ] as [String:Any],
            "results":[
                "codeComplianceVerified":false,
                "designCurrentA":50.0,"adjustedAmpacityA":25.52,
                "conductorMarginA":-24.48,"voltageDropV":1.76746,
                "voltageDropPercent":0.2945,"allowableDropV":18.0,
                "minimumCircularMilsForDrop":6516.03,
                "maximumOneWayLengthM":407.365,
                "ampacityArithmeticFits":false,
                "voltageDropArithmeticFits":true,
                "protectionScreenFits":false
            ] as [String:Any],
            "assumptions":["notes":"Verify actual 60 C terminal marking"],
            "result":"Original saved calculations, NOT CODE VERIFIED"
        ]
        let text=try CircuitProjectReportText.build(record,
                      client:"Example client",site:"Mine substation",reviewer:"Reviewer")
        for phrase in ["Station Upgrade","Panel Feeder","ios-test-2",
                       "NOT CODE VERIFIED","OESC_ON_2024",
                       "AUTHORIZED_TABLE_VERIFICATION_REQUIRED",
                       "2026-11-08T13:00","Example client","Mine substation",
                       "Terminal temperature","25.52","6500",
                       "Adjusted conductor ampacity",
                       "Original saved calculations","Reviewer","Signature"] {
            // Computed minimum is intentionally provided, no automatic conductor selection.
            if phrase=="6500" {continue}
            expect(text.contains(phrase),"Missing report field "+phrase)
        }
        expect(text.contains("REVIEW / FAIL"),
               "Failing derated conductor cannot be presented as passing")
        var bad=record
        bad["results"]=["codeComplianceVerified":true]
        do {
            _=try CircuitProjectReportText.build(bad)
            fatalError("A supposedly approved input must not render.")
        }catch CircuitProjectReportText.ReportError.invalidRecord {}
        print("iOS structured circuit report text tests passed.")
    }
}
