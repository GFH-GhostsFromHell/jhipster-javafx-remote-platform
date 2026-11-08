import Foundation

/// Offline arithmetic used for nameplate review, not Code compliance or approved relay settings.
enum IndustrialWorksheet: String, CaseIterable, Identifiable {
    case ct, vt, power, pickup, fault, analog
    var id:String {rawValue}
    var title:String {
        switch self {
        case .ct: "CT current transformation"
        case .vt: "VT / PT voltage transformation"
        case .power: "Balanced three-phase power"
        case .pickup: "CT-based relay pickup translation"
        case .fault: "Ideal transformer secondary fault estimate"
        case .analog: "4–20 mA analog instrument scaling"
        }
    }
    var fields:[IndustrialField] {
        switch self {
        case .ct: [
            .init("primaryRatedA","CT rated primary (A)","400"),
            .init("secondaryRatedA","CT rated secondary (A)","5"),
            .init("actualPrimaryA","Measured primary current (A)","200")]
        case .vt: [
            .init("primaryRatedV","VT rated primary (V)","4160"),
            .init("secondaryRatedV","VT rated secondary (V)","120"),
            .init("actualPrimaryV","Measured primary voltage (V)","4000")]
        case .power: [
            .init("lineVoltageV","Line-to-line voltage (V)","600"),
            .init("lineCurrentA","Line current (A)","100"),
            .init("powerFactor","Power factor 0–1","0.85")]
        case .pickup: [
            .init("primaryRatedA","CT rated primary (A)","400"),
            .init("secondaryRatedA","CT rated secondary (A)","5"),
            .init("primaryPickupA","Already specified primary pickup (A)","240")]
        case .fault: [
            .init("transformerKva","Transformer nameplate (kVA)","75"),
            .init("secondaryVoltageV","Secondary line voltage (V)","600"),
            .init("impedancePercent","Nameplate impedance (%)","5")]
        case .analog: [
            .init("engineeringMin","Engineering value at 4 mA","0"),
            .init("engineeringMax","Engineering value at 20 mA","1800"),
            .init("engineeringValue","Required engineering value","1200")]
        }
    }
}
struct IndustrialField:Identifiable {
    let id:String
    let label:String
    let example:String
    init(_ id:String,_ label:String,_ example:String){
        self.id=id;self.label=label;self.example=example
    }
}
struct IndustrialFieldResult {
    let readings:[(String,Double)]
    let caution:String
    let codeComplianceVerified=false
}
enum IndustrialFieldMath {
    enum MathError:LocalizedError {
        case invalid(String)
        var errorDescription:String? {
            switch self { case .invalid(let message):message }
        }
    }
    static func calculate(_ mode:IndustrialWorksheet,
                          values:[String:Double]) throws -> IndustrialFieldResult {
        func finite(_ name:String) throws -> Double {
            guard let value=values[name],value.isFinite else {
                throw MathError.invalid("Enter a finite value for \(name).")
            }
            return value
        }
        func positive(_ name:String) throws -> Double {
            let value=try finite(name)
            guard value>0 else {throw MathError.invalid("\(name) must be positive.")}
            return value
        }
        func result(_ readings:[(String,Double)],_ caution:String) throws -> IndustrialFieldResult {
            guard readings.allSatisfy({$0.1.isFinite}) else {
                throw MathError.invalid("Calculated value is outside the supported numeric range.")
            }
            return IndustrialFieldResult(readings:readings,caution:caution)
        }
        switch mode {
        case .ct:
            let p=try positive("primaryRatedA"),s=try positive("secondaryRatedA")
            let actual=try positive("actualPrimaryA")
            return try result([("CT ratio",p/s),("Secondary current A",actual*s/p),
                               ("Reconstructed primary A",(actual*s/p)*p/s)],
                        "Ideal CT ratio only. Check saturation, polarity, burden, accuracy class and secondary circuit.")
        case .vt:
            let p=try positive("primaryRatedV"),s=try positive("secondaryRatedV")
            let actual=try positive("actualPrimaryV")
            return try result([("VT ratio",p/s),("Secondary voltage V",actual*s/p)],
                        "Ideal voltage ratio only. Verify nameplate, fuses, grounding and burden.")
        case .power:
            let v=try positive("lineVoltageV"),a=try positive("lineCurrentA")
            let pf=try positive("powerFactor")
            guard pf<=1 else {throw MathError.invalid("Power factor must not exceed one.")}
            let kva=sqrt(3)*v*a/1000
            return try result([("Apparent power kVA",kva),("Real power kW",kva*pf)],
                        "Balanced sinusoidal power estimate. Does not analyze unbalance or harmonics.")
        case .pickup:
            let p=try positive("primaryRatedA"),s=try positive("secondaryRatedA")
            let pickup=try positive("primaryPickupA")
            return try result([("CT ratio",p/s),("Secondary pickup A",pickup*s/p)],
                        "Translation of an already specified pickup only. NOT approved relay settings; verify coordination study and device.")
        case .fault:
            let kva=try positive("transformerKva"),v=try positive("secondaryVoltageV")
            let z=try positive("impedancePercent")
            guard z<100 else {throw MathError.invalid("Impedance must be below 100%.")}
            let fla=kva*1000/(sqrt(3)*v)
            return try result([("Transformer FLA A",fla),("IDEAL infinite-source fault A",fla*100/z)],
                        "NOT actual available fault current or arc flash rating. Infinite upstream source assumed, other contributions excluded.")
        case .analog:
            let min=try finite("engineeringMin"),max=try finite("engineeringMax")
            let value=try finite("engineeringValue")
            guard max>min && value>=min && value<=max else {
                throw MathError.invalid("Engineering span must increase; requested value must fit within it.")
            }
            let fraction=(value-min)/(max-min)
            return try result([("Analog current mA",4+16*fraction),("Span percent",100*fraction)],
                        "Linear 4–20 mA scaling only. Verify device configuration and fail-safe behavior.")
        }
    }
}
