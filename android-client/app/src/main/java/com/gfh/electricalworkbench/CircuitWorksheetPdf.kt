package com.gfh.electricalworkbench

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.Instant

/**
 * A saved revision's PDF is generated entirely on the Android device.
 * Source table values are not independently verified; this is NOT an approval.
 */
internal object CircuitWorksheetPdf {
    fun render(record: JSONObject, client: String, site: String, reviewer: String): ByteArray {
        require(record.optString("kind") == "circuitWorksheet") {
            "Choose a saved circuit worksheet revision."
        }
        val input = requireNotNull(record.optJSONObject("inputs")) { "Missing saved inputs." }
        val values = requireNotNull(record.optJSONObject("results")) { "Missing saved results." }
        require(!values.optBoolean("codeComplianceVerified", true)) {
            "Unverified calculation report expected; approval cannot be asserted."
        }
        val assumptions = record.optJSONObject("assumptions") ?: JSONObject()

        val lines = mutableListOf<Pair<String, Boolean>>()
        fun header(text: String) { lines.add(text to true) }
        fun line(text: String) { lines.add(text to false) }
        fun entry(label: String, obj: JSONObject, key: String, suffix: String = "") {
            line("$label: " + if (obj.has(key) && !obj.isNull(key)) {
                obj.optString(key, "Not supplied") + suffix
            } else "Not supplied")
        }
        header("ELECTRICAL WORKBENCH - CIRCUIT CALCULATION RECORD")
        line("PRELIMINARY / NOT CODE VERIFIED / NOT AN INSTALLATION APPROVAL")
        line("")
        header("PROJECT & REVISION")
        entry("Project", record, "project")
        entry("Worksheet title", record, "title")
        entry("Revision", record, "revision")
        entry("Saved UTC", record, "saved")
        line("Record ID: " + record.optString("_syncId", record.optString("id")))
        line("PDF generated UTC: " + Instant.now())
        line("Client / employer: " + client.ifBlank { "Not supplied" })
        line("Site / location: " + site.ifBlank { "Not supplied" })
        line("")
        header("CODE SOURCE & VERIFICATION")
        line("Reference profile: OESC_ON_2024 / CSA C22.1:24")
        line("Numeric table verification: AUTHORIZED_TABLE_VERIFICATION_REQUIRED")
        entry("Code edition", record, "codeEdition")
        entry("Jurisdiction", record, "jurisdiction")
        entry("Entered tables", record, "referenceTables")
        line("Equipment termination markings verified: NO")
        line("Code compliance verified: NO")
        line("")
        header("ENTERED CIRCUIT AND CONDUCTOR DETAILS")
        entry("Actual load current", input, "loadA", " A")
        entry("General sizing multiplier", input, "designMultiplier")
        entry("System voltage", input, "voltageV", " V")
        entry("Circuit phases", input, "phases")
        entry("One-way length", input, "oneWayLengthM", " m")
        entry("Conductor size / description", input, "conductorDescription")
        entry("Ampacity table reference", input, "tableReference")
        entry("Termination temperature", input, "terminalC", " C")
        entry("Insulation temperature", input, "insulationC", " C")
        entry("User-entered termination-column ampacity", input, "terminalColumnAmpacityA", " A")
        entry("Ambient correction factor", input, "ambientFactor")
        entry("Grouping correction factor", input, "groupingFactor")
        entry("Conductor circular mils", input, "circularMils")
        entry("Resistance K", input, "kOhmCircularMilPerFoot", " ohm-cmil/ft")
        entry("Voltage drop target", input, "maxVoltageDropPercent", " %")
        entry("Selected protection device", input, "selectedOcpdA", " A")
        line("")
        header("CALCULATED ARITHMETIC RESULTS")
        entry("Required design current", values, "designCurrentA", " A")
        entry("Corrected conductor ampacity", values, "adjustedAmpacityA", " A")
        entry("Ampacity margin", values, "conductorMarginA", " A")
        entry("Estimated voltage drop", values, "voltageDropV", " V")
        entry("Estimated voltage drop", values, "voltageDropPercent", " %")
        entry("Allowable entered drop", values, "allowableDropV", " V")
        entry("Min circular mils for VD target", values, "minimumCircularMilsForDrop")
        entry("Max one-way length for selected area", values, "maximumOneWayLengthM", " m")
        line("Ampacity screen: " + if (values.optBoolean("ampacityArithmeticFits")) "WITHIN INPUT LIMIT" else "REVIEW / FAIL")
        line("Voltage drop screen: " + if (values.optBoolean("voltageDropArithmeticFits")) "WITHIN INPUT TARGET" else "REVIEW / FAIL")
        line("OCPD screen: " + if (values.optBoolean("protectionScreenFits")) "WITHIN INPUT BOUNDS" else "REVIEW")
        line("")
        header("FIELD NOTES / CODE ASSUMPTIONS")
        entry("Field notes", assumptions, "notes")
        line("Voltage drop uses approximate resistive K-factor.")
        line("Upstream feeder drop, AC reactance and special circuits not verified.")
        line("")
        header("ORIGINAL WORKED CALCULATION")
        record.optString("result", "Not supplied").replace("\r", "")
            .split("\n").forEach { line(it) }
        line("")
        header("REVIEWER / SIGNATURE")
        line("Reviewed by: " + reviewer.ifBlank { "________________________" })
        line("Reviewed on: ______________________")
        line("Signature: _________________________")
        line("This report does not provide an engineering seal or electrical approval.")
        line("")
        line("Check official CEC/OESC tables, equipment markings, installation,")
        line("neutral count, conductor correction, OCPD and bonding.")

        val pdf=PdfDocument()
        val black=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.rgb(33,48,67);textSize=10f
        }
        val bold=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.rgb(15,80,105);textSize=11.3f;isFakeBoldText=true
        }
        val paperWidth=612
        val paperHeight=792
        val left=42f
        val available=paperWidth-84f
        val bottom=paperHeight-46f
        var pageNo=0
        var current: PdfDocument.Page?=null
        var y=0f
        fun newPage() {
            current?.let { pdf.finishPage(it) }
            pageNo++
            current=pdf.startPage(PdfDocument.PageInfo.Builder(paperWidth,paperHeight,pageNo).create())
            val p=current!!.canvas
            p.drawText("ELECTRICAL WORKBENCH  |  " + record.optString("project").take(40) +
                "  |  Page $pageNo",left,28f,bold)
            p.drawLine(left,35f,paperWidth-left,35f,bold)
            y=54f
        }
        fun wrap(text: String, paint: Paint): List<String> {
            if(text.isEmpty())return listOf("")
            val words=text.split(Regex("\\s+"))
            val wrapped=mutableListOf<String>()
            var currentLine=""
            for(word in words){
                if(paint.measureText(word)>available){
                    if(currentLine.isNotEmpty()){wrapped.add(currentLine);currentLine=""}
                    var fragment=""
                    for(c in word){
                        if(paint.measureText(fragment+c)>available&&fragment.isNotEmpty()){
                            wrapped.add(fragment);fragment=""
                        }
                        fragment+=c
                    }
                    currentLine=fragment
                }else{
                    val combined=if(currentLine.isEmpty())word else "$currentLine $word"
                    if(paint.measureText(combined)>available&&currentLine.isNotEmpty()){
                        wrapped.add(currentLine);currentLine=word
                    }else currentLine=combined
                }
            }
            wrapped.add(currentLine)
            return wrapped
        }
        try {
            newPage()
            for((raw,isHeader) in lines){
                val paint=if(isHeader)bold else black
                for(text in wrap(raw,paint)){
                    val lineHeight=if(isHeader)17f else 14f
                    if(y+lineHeight>bottom)newPage()
                    current!!.canvas.drawText(text,left,y,paint)
                    y+=lineHeight
                }
                if(isHeader)y+=4f
            }
            current?.let { pdf.finishPage(it) }
            val out=ByteArrayOutputStream()
            pdf.writeTo(out)
            return out.toByteArray()
        } finally { pdf.close() }
    }
}
