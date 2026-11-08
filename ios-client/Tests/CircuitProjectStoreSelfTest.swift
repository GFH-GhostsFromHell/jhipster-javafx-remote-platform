import Foundation

// The production app declares this notification in AccountRecordSync.swift.
// Self-tests use Foundation only, avoiding a full SwiftUI app launch.
extension Notification.Name {
    static let ewbLocalRecordChanged = Notification.Name("ewb.local.record.changed")
}

@main struct CircuitProjectStoreSelfTest {
    @MainActor static func main() throws {
        func check(_ condition:Bool,_ reason:String) {
            if !condition {fatalError("Project archive test: "+reason)}
        }
        let user="CI-"+UUID().uuidString
        let input=UnifiedCircuitMath.Input(
            loadA:40,designMultiplier:1.25,voltageV:600,phases:3,
            oneWayLengthM:40,circularMils:66360,kOhmCircularMilPerFoot:12.9,
            terminalColumnAmpacityA:85,terminalC:75,insulationC:90,
            ambientFactor:1,groupingFactor:1,selectedOcpdA:50,maxVoltageDropPercent:3)
        guard let results=UnifiedCircuitMath.calculate(input) else {
            fatalError("Base circuit scenario failed.")
        }
        check(CircuitProjectStore.revision(rows:[],project:"Mine",title:"Feeder")==1,"first rev")
        let first=try CircuitProjectStore.save(user:user,project:"Mine",title:"Feeder",
            notes:"Check 75°C terminations",conductor:"4 AWG Cu example",
            table:"Table 2",input:input,result:results,report:"NOT CODE VERIFIED")
        let second=try CircuitProjectStore.save(user:user,project:"Mine",title:"Feeder",
            notes:"Retested",conductor:"4 AWG Cu example",
            table:"Table 2",input:input,result:results,report:"Revised result; NOT CODE VERIFIED")
        let other=try CircuitProjectStore.save(user:user,project:"Mine",title:"Branch",
            notes:"",conductor:"Copper example",
            table:"Table 2",input:input,result:results,report:"NOT CODE VERIFIED")
        check(first==1 && second==2 && other==1,"project/title scoped revision counting")
        let rows=CircuitProjectStore.rows(user:user,project:"Mine")
        check(rows.count==3,"all revisions preserved")
        let original=rows.first { $0.revision==1 && $0.title=="Feeder" }
        check(original != nil,"original revision not overwritten")
        let inputSnapshot=original?.raw["inputs"] as? [String:Any]
        check((inputSnapshot?["loadA"] as? NSNumber)?.doubleValue==40,
              "entered current saved")
        let values=original?.raw["results"] as? [String:Any]
        check((values?["codeComplianceVerified"] as? Bool)==false,
              "results must never claim compliance")
        check((original?.raw["saved"] as? String)?.contains("T")==true,
              "ISO dated revision preserved")
        let notes=(original?.raw["assumptions"] as? [String:Any])?["notes"] as? String
        check(notes=="Check 75°C terminations","field assumptions preserved")
        let backup=CircuitProjectStore.exportJSON(user:user,project:"Mine")
        check(backup.contains("ElectricalWorkbenchCircuitProject") &&
              backup.contains("Revised result"),"export includes all revisions")
        check(CircuitProjectStore.rows(user:user,project:"Shop").isEmpty,
              "project filtering")
        UserDefaults.standard.removeObject(forKey:"ewb.ios.circuitWorksheets."+user)
        print("iOS project worksheet revisions, structured snapshots and export self-tests passed.")
    }
}
