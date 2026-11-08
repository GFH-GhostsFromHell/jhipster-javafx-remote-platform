package com.gfh.electricalworkbench

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/**
 * Local-first project worksheets in the already synchronized designReports bucket.
 * Revisions are append-only: saving a recalculation never overwrites field evidence.
 */
internal object CircuitWorksheetStore {
    private const val KEY = "records_designReports"
    private const val MAX_ROWS = 5000

    fun records(context: Context, username: String): List<JSONObject> {
        val prefs = prefs(context, username)
        val all = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        return (0 until all.length()).mapNotNull { all.optJSONObject(it) }
            .filter { it.optString("kind") == "circuitWorksheet" }
            .sortedByDescending { it.optString("saved") }
    }

    fun save(
        context: Context, username: String, project: String, title: String, notes: String,
        inputs: JSONObject, results: JSONObject, report: String
    ): JSONObject {
        val p=project.trim()
        val t=title.trim()
        require(p.isNotEmpty() && p.length<=150 && t.isNotEmpty() && t.length<=150) {
            "Project and worksheet title must contain 1–150 characters."
        }
        require(notes.length<=2000) { "Notes must be no longer than 2,000 characters." }
        require(report.isNotBlank() && results.optBoolean("codeComplianceVerified",true).not()) {
            "Review a valid circuit calculation before saving."
        }
        val prefs=prefs(context,username)
        synchronized(this) {
            val all=runCatching { JSONArray(prefs.getString(KEY,"[]")) }.getOrDefault(JSONArray())
            require(all.length()<MAX_ROWS) {
                "Worksheet archive is full. Export or back up records before saving more."
            }
            var current=0
            for(i in 0 until all.length()) {
                val r=all.optJSONObject(i)?:continue
                if(r.optString("kind")=="circuitWorksheet" &&
                   r.optString("project")==p && r.optString("title")==t)
                    current=maxOf(current,r.optInt("revision",0))
            }
            val id=UUID.randomUUID().toString()
            val record=JSONObject()
                .put("id",id).put("_syncId",id).put("kind","circuitWorksheet")
                .put("schemaVersion",1).put("project",p).put("title",t)
                .put("revision",current+1).put("saved",Instant.now().toString())
                .put("codeEdition","CEC_2024")
                .put("jurisdiction","Ontario (verify effective OESC amendments)")
                .put("referenceTables",inputs.optString("tableReference","Table 2")+"; Tables 5A and 5C (user-supplied)")
                .put("assumptions",JSONObject()
                    .put("conductorTableValuesVerified",false)
                    .put("terminationMarkingVerified",false)
                    .put("codeComplianceVerified",false)
                    .put("voltageDropMethod","resistive K-factor estimate, not full AC impedance")
                    .put("specialCircuitRulesReviewed",false)
                    .put("notes",notes))
                .put("inputs",inputs).put("results",results).put("result",report)
            all.put(record)
            check(prefs.edit().putString(KEY,all.toString()).commit()) {
                "Unable to persist worksheet on this device."
            }
            MobileAccountSync.schedule(context.applicationContext)
            return record
        }
    }

    private fun prefs(context: Context, username: String): android.content.SharedPreferences {
        require(username.isNotBlank()) { "Sign in to save a project worksheet to your account." }
        val safe=username.replace(Regex("[^A-Za-z0-9._-]"),"_")
        return context.getSharedPreferences("electrical_workbench_"+safe,Context.MODE_PRIVATE)
    }
}
