import Foundation

@main struct IndustrialFieldMathSelfTest {
    static func near(_ actual:Double,_ expected:Double,tolerance:Double=0.0001){
        guard actual.isFinite,abs(actual-expected)<tolerance else {
            fatalError("Expected \(expected), got \(actual)")
        }
    }
    static func check(_ value:Bool,_ reason:String){
        if !value {fatalError("Industrial regression: \(reason)")}
    }
    static func main() throws {
        let ct=try IndustrialFieldMath.calculate(.ct,values:[
            "primaryRatedA":400,"secondaryRatedA":5,"actualPrimaryA":200])
        near(ct.readings[0].1,80)
        near(ct.readings[1].1,2.5)
        check(!ct.codeComplianceVerified,"CT may not be Code approved")
        let vt=try IndustrialFieldMath.calculate(.vt,values:[
            "primaryRatedV":4160,"secondaryRatedV":120,"actualPrimaryV":4000])
        near(vt.readings[1].1,115.384615)
        let power=try IndustrialFieldMath.calculate(.power,values:[
            "lineVoltageV":600,"lineCurrentA":100,"powerFactor":0.85])
        near(power.readings[0].1,103.923048)
        near(power.readings[1].1,88.33459)
        let pickup=try IndustrialFieldMath.calculate(.pickup,values:[
            "primaryRatedA":400,"secondaryRatedA":5,"primaryPickupA":240])
        near(pickup.readings[1].1,3)
        check(pickup.caution.contains("NOT approved"),"Pickup limitation must be explicit")
        let fault=try IndustrialFieldMath.calculate(.fault,values:[
            "transformerKva":75,"secondaryVoltageV":600,"impedancePercent":5])
        near(fault.readings[0].1,72.16878365)
        near(fault.readings[1].1,1443.375673)
        check(fault.caution.contains("NOT actual available fault current"),"Short-circuit limitation")
        let analog=try IndustrialFieldMath.calculate(.analog,values:[
            "engineeringMin":0,"engineeringMax":1800,"engineeringValue":1200])
        near(analog.readings[0].1,14.6666667)
        check(IndustrialWorksheet.allCases.count==6,"Six worksheets")
        check(IndustrialWorksheet.allCases.allSatisfy{$0.fields.count==3},"Three inputs each")
        check((try? IndustrialFieldMath.calculate(.power,values:[
            "lineVoltageV":600,"lineCurrentA":100,"powerFactor":1.2]))==nil,"PF invalid")
        check((try? IndustrialFieldMath.calculate(.fault,values:[
            "transformerKva":75,"secondaryVoltageV":600,"impedancePercent":100]))==nil,"Z invalid")
        check((try? IndustrialFieldMath.calculate(.analog,values:[
            "engineeringMin":0,"engineeringMax":1800,"engineeringValue":1900]))==nil,"mA out of range")
        check((try? IndustrialFieldMath.calculate(.ct,values:[
            "primaryRatedA":400,"secondaryRatedA":5,"actualPrimaryA":Double.nan]))==nil,"NaN forbidden")
        print("iOS industrial field self-tests passed: six methods, safety checks and cross-platform arithmetic.")
    }
}
