import Foundation

/// Append-only offline circuit revision archives, stored in the existing
/// designReports account-sync collection when the user is signed in.
struct CircuitSavedRevision: Identifiable {
    let id: String
    let project: String
    let title: String
    let revision: Int
    let saved: String
    let raw: [String: Any]
}

@MainActor
enum CircuitProjectStore {
    private static func key(_ user: String) -> String {
        "ewb.ios.circuitWorksheets.\(user)"
    }

    static func all(_ user: String) -> [[String: Any]] {
        guard !user.isEmpty,
              let data=UserDefaults.standard.data(forKey:key(user)),
              let rows=(try? JSONSerialization.jsonObject(with:data)) as? [[String:Any]]
        else { return [] }
        return rows
    }

    static func rows(user: String, project: String) -> [CircuitSavedRevision] {
        all(user).compactMap { raw in
            guard raw["kind"] as? String == "circuitWorksheet",
                  let id=raw["_syncId"] as? String ?? raw["id"] as? String,
                  let projectName=raw["project"] as? String,
                  projectName == project,
                  let title=raw["title"] as? String else { return nil }
            return CircuitSavedRevision(id:id,project:projectName,title:title,
                revision:(raw["revision"] as? NSNumber)?.intValue ?? 0,
                saved:raw["saved"] as? String ?? "",raw:raw)
        }.sorted { $0.saved > $1.saved }
    }

    static func revision(rows: [[String:Any]],project: String,title: String) -> Int {
        1+(rows.filter {
            ($0["kind"] as? String) == "circuitWorksheet" &&
            ($0["project"] as? String) == project &&
            ($0["title"] as? String) == title
        }.map {($0["revision"] as? NSNumber)?.intValue ?? 0}.max() ?? 0)
    }

    @discardableResult
    static func save(user: String,project: String,title: String,notes: String,
                     conductor: String,table: String,
                     input: UnifiedCircuitMath.Input,
                     result: UnifiedCircuitMath.Result,report: String) throws -> Int {
        let p=project.trimmingCharacters(in:.whitespacesAndNewlines)
        let t=title.trimmingCharacters(in:.whitespacesAndNewlines)
        guard !user.isEmpty else {throw SaveError.message("Sign in before saving an account-linked worksheet.")}
        guard !p.isEmpty && p.count<=150 && !t.isEmpty && t.count<=150 else {
            throw SaveError.message("Project and worksheet title must contain 1–150 characters.")
        }
        guard notes.count<=2000 && !report.isEmpty else {
            throw SaveError.message("Enter no more than 2,000 notes characters and calculate first.")
        }
        var data=all(user)
        let next=revision(rows:data,project:p,title:t)
        let id=UUID().uuidString
        let inputs:[String:Any]=[
            "loadA":input.loadA,"designMultiplier":input.designMultiplier,
            "voltageV":input.voltageV,"phases":input.phases,
            "oneWayLengthM":input.oneWayLengthM,"circularMils":input.circularMils,
            "kOhmCircularMilPerFoot":input.kOhmCircularMilPerFoot,
            "terminalColumnAmpacityA":input.terminalColumnAmpacityA,
            "terminalC":input.terminalC,"insulationC":input.insulationC,
            "ambientFactor":input.ambientFactor,"groupingFactor":input.groupingFactor,
            "selectedOcpdA":input.selectedOcpdA,
            "maxVoltageDropPercent":input.maxVoltageDropPercent,
            "conductorDescription":conductor,"tableReference":table
        ]
        let numeric:[String:Any]=[
            "designCurrentA":result.designCurrentA,
            "adjustedAmpacityA":result.adjustedAmpacityA,
            "conductorMarginA":result.conductorMarginA,
            "voltageDropV":result.voltageDropV,
            "voltageDropPercent":result.voltageDropPercent,
            "allowableDropV":result.allowableDropV,
            "minimumCircularMilsForDrop":result.minimumCircularMilsForDrop,
            "maximumOneWayLengthM":result.maximumOneWayLengthM,
            "ampacityArithmeticFits":result.ampacityArithmeticFits,
            "voltageDropArithmeticFits":result.voltageDropArithmeticFits,
            "protectionScreenFits":result.protectionScreenFits,
            "codeComplianceVerified":false
        ]
        let revision:[String:Any]=[
            "id":id,"_syncId":id,"kind":"circuitWorksheet","schemaVersion":1,
            "project":p,"title":t,"revision":next,
            "saved":ISO8601DateFormatter().string(from:Date()),
            "codeEdition":"CEC_2024",
            "referenceProfileId":"OESC_ON_2024",
            "numericDatasetVerificationStatus":"AUTHORIZED_TABLE_VERIFICATION_REQUIRED",
            "numericDatasetVerified":false,
            "jurisdiction":"Ontario (verify effective OESC amendments)",
            "referenceTables":table+"; Tables 5A and 5C (user-supplied)",
            "assumptions":[
                "conductorTableValuesVerified":false,
                "terminationMarkingVerified":false,
                "codeComplianceVerified":false,
                "specialCircuitRulesReviewed":false,
                "voltageDropMethod":"resistive K-factor estimate, not full AC impedance",
                "notes":notes
            ] as [String:Any],
            "inputs":inputs,"results":numeric,"result":report
        ]
        data.append(revision)
        guard JSONSerialization.isValidJSONObject(data) else {
            throw SaveError.message("Invalid worksheet data; no changes saved.")
        }
        let encoded=try JSONSerialization.data(withJSONObject:data,options:[.sortedKeys])
        UserDefaults.standard.set(encoded,forKey:key(user))
        NotificationCenter.default.post(name:.ewbLocalRecordChanged,object:"designReports")
        return next
    }

    static func exportJSON(user:String,project:String) -> String {
        let records=rows(user:user,project:project).map{$0.raw}
        guard !records.isEmpty else {return "No saved worksheets for this project."}
        let payload:[String:Any]=[
            "format":"ElectricalWorkbenchCircuitProject","schemaVersion":1,
            "project":project,"exported":ISO8601DateFormatter().string(from:Date()),
            "worksheets":records
        ]
        guard let data=try? JSONSerialization.data(withJSONObject:payload,options:[.prettyPrinted,.sortedKeys]),
              let text=String(data:data,encoding:.utf8) else{return "Could not export."}
        return text
    }

    enum SaveError: LocalizedError {
        case message(String)
        var errorDescription: String? {
            switch self { case .message(let text):return text }
        }
    }
}
