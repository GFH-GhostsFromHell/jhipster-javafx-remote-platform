package com.gfh.electricalworkbench

import kotlin.math.sqrt

/** Offline nameplate/measurement arithmetic only. Never designs a relay setting. */
internal object IndustrialFieldMath {
    data class Field(val id:String,val label:String,val example:String)
    data class Mode(val id:String,val title:String,val fields:List<Field>)
    data class Result(val values:Map<String,Double>,val caution:String,
                      val codeComplianceVerified:Boolean=false)
    val modes=listOf(
        Mode("ct","CT current transformation",listOf(
            Field("primaryRatedA","CT rated primary A","400"),
            Field("secondaryRatedA","CT rated secondary A","5"),
            Field("actualPrimaryA","Primary current A","200"))),
        Mode("vt","VT / PT voltage transformation",listOf(
            Field("primaryRatedV","VT rated primary V","4160"),
            Field("secondaryRatedV","VT rated secondary V","120"),
            Field("actualPrimaryV","Measured primary V","4000"))),
        Mode("power","Three-phase apparent and real power",listOf(
            Field("lineVoltageV","Line-to-line voltage V","600"),
            Field("lineCurrentA","Line current A","100"),
            Field("powerFactor","Power factor 0–1","0.85"))),
        Mode("pickup","CT relay pickup translation",listOf(
            Field("primaryRatedA","CT rated primary A","400"),
            Field("secondaryRatedA","CT rated secondary A","5"),
            Field("primaryPickupA","Already specified primary pickup A","240"))),
        Mode("fault","Ideal transformer fault contribution",listOf(
            Field("transformerKva","Transformer kVA","75"),
            Field("secondaryVoltageV","Secondary line voltage V","600"),
            Field("impedancePercent","Nameplate impedance %","5"))),
        Mode("analog","4–20 mA instrumentation span",listOf(
            Field("engineeringMin","Engineering value at 4 mA","0"),
            Field("engineeringMax","Engineering value at 20 mA","1800"),
            Field("engineeringValue","Requested engineering value","1200")))
    )
    fun calculate(mode:String, input:Map<String,Double>):Result {
        fun finite(key:String):Double {
            val x=input[key] ?: throw IllegalArgumentException("Missing $key")
            require(x.isFinite()) { "$key must be finite." }
            return x
        }
        fun positive(key:String):Double {
            val x=finite(key)
            require(x>0) { "$key must be positive." }
            return x
        }
        fun result(vararg items:Pair<String,Double>,caution:String):Result {
            require(items.all { it.second.isFinite() }){"Computed value outside supported range."}
            return Result(linkedMapOf(*items),caution)
        }
        return when(mode){
            "ct"-> {
                val p=positive("primaryRatedA")
                val s=positive("secondaryRatedA")
                val i=positive("actualPrimaryA")
                result("CT ratio" to p/s,"Secondary current A" to i*s/p,
                    "Reconstructed primary A" to (i*s/p)*p/s,
                    caution="Ideal CT arithmetic only. Verify polarity, burden, saturation, accuracy class and terminal wiring.")
            }
            "vt"->{
                val p=positive("primaryRatedV")
                val s=positive("secondaryRatedV")
                val v=positive("actualPrimaryV")
                result("VT ratio" to p/s,"Secondary voltage V" to v*s/p,
                    caution="Ideal VT translation; verify nameplate, primary fuse, grounding and installation.")
            }
            "power"->{
                val v=positive("lineVoltageV")
                val i=positive("lineCurrentA")
                val pf=positive("powerFactor")
                require(pf<=1){"Power factor must not exceed 1."}
                val kva=sqrt(3.0)*v*i/1000.0
                result("Apparent power kVA" to kva,"Real power kW" to kva*pf,
                    caution="Balanced sinusoidal three-phase estimate; unbalance and harmonics excluded.")
            }
            "pickup"->{
                val p=positive("primaryRatedA")
                val s=positive("secondaryRatedA")
                val pickup=positive("primaryPickupA")
                result("CT ratio" to p/s,"Secondary pickup A" to pickup*s/p,
                    caution="Converts an already specified pickup only. NOT a recommended relay setting. Verify full protection coordination study.")
            }
            "fault"->{
                val kva=positive("transformerKva")
                val v=positive("secondaryVoltageV")
                val z=positive("impedancePercent")
                require(z<100){"Impedance must be less than 100%."}
                val fla=kva*1000.0/(sqrt(3.0)*v)
                result("Transformer FLA A" to fla,
                    "IDEAL infinite-source secondary fault A" to fla*100.0/z,
                    caution="NOT actual available fault current or arc-flash rating. Assumes infinite source, only transformer Z%, symmetrical short circuit.")
            }
            "analog"->{
                val min=finite("engineeringMin")
                val max=finite("engineeringMax")
                val value=finite("engineeringValue")
                require(max>min){"20mA endpoint must exceed 4mA endpoint."}
                require(value>=min&&value<=max){"Requested value lies outside configured span."}
                val frac=(value-min)/(max-min)
                result("Analog current mA" to 4.0+16.0*frac,"Span percent" to frac*100.0,
                    caution="Linear analog translation only; verify device configuration, fail-safe, wiring and calibration.")
            }
            else->throw IllegalArgumentException("Unsupported industrial field worksheet.")
        }
    }
}
