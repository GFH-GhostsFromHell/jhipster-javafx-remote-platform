package com.gfh.electricalworkbench

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * Local-first per-account record synchronization. Password is held in RAM only.
 * The server owns revisions and returns conflicts instead of accepting stale overwrites.
 */
internal object MobileAccountSync {
    private const val ROOT = "https://remote-javafx-portal.onrender.com"
    private val buckets = listOf(
        "projects","materials","nameplates","commissioning","inspections",
        "hours","credentials","quizHistory","unsafeConditions","rubberGloves",
        "safetyMaintenance","stormStandDowns","safetyAssessments",
        "guidedSolutions","practiceDrafts","designReports","estimates")
    @Volatile private var username: String? = null
    @Volatile private var password: String? = null
    @Volatile var status: String = "Sync after sign-in"
        private set

    fun configure(user: String, secret: String) {
        username = user
        password = secret
        status = "Signed in · syncing records when online"
    }

    fun disconnect() {
        username = null
        password = null
        status = "Sync disconnected"
    }

    fun schedule(context: Context) {
        if (username == null) return
        Thread {
            runCatching { syncAll(context.applicationContext) }
                .onFailure { status = "Offline: changes saved locally; ${it.message ?: "sync unavailable"}" }
        }.start()
    }

    @Synchronized fun syncAll(context: Context) {
        val user = username ?: return
        val secret = password ?: return
        val prefs = context.getSharedPreferences("electrical_workbench_" + user.replace(Regex("[^A-Za-z0-9._-]"), "_"), Context.MODE_PRIVATE)
        var count = 0
        for (bucket in buckets) {
            if (username != user || password != secret) return
            syncBucket(bucket, prefs, user, secret)
            count++
        }
        syncCalibration(context,user,secret)
        status = "Account sync checked $count collections"
    }

    private fun request(path: String, user: String, secret: String, payload: JSONObject? = null): String {
        val con = URL(ROOT + path).openConnection() as HttpURLConnection
        val auth = "$user:$secret"
        con.setRequestProperty("Authorization","Basic " + Base64.encodeToString(auth.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP))
        con.setRequestProperty("Accept", "application/json")
        con.connectTimeout = 15_000
        con.readTimeout = 30_000
        if (payload != null) {
            con.requestMethod = "POST"
            con.doOutput = true
            con.setRequestProperty("Content-Type", "application/json")
            con.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
        }
        try {
            val code = con.responseCode
            val text = (if (code in 200..299) con.inputStream else con.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code == 401 || code == 403) throw IllegalStateException("Account session invalid; sign in again.")
            if (code !in 200..299) throw IllegalStateException("Sync server returned HTTP $code")
            return text
        } finally { con.disconnect() }
    }

    private fun list(bucket: String, user: String, secret: String): JSONArray =
        JSONArray(request("/api/sync/$bucket",user,secret))

    private fun newId(): String = UUID.randomUUID().toString()

    private fun syncBucket(bucket: String, prefs: android.content.SharedPreferences, user: String, secret: String) {
        val localKey = "records_$bucket"
        val mirrorKey = "sync_meta_$bucket"
        val rows = runCatching { JSONArray(prefs.getString(localKey,"[]")) }.getOrDefault(JSONArray())
        val local = linkedMapOf<String,JSONObject>()
        for (index in 0 until rows.length()) {
            val obj = rows.optJSONObject(index) ?: continue
            val id = obj.optString("_syncId").ifBlank { newId() }
            obj.put("_syncId",id)
            local[id] = obj
        }
        val before = runCatching { JSONObject(prefs.getString(mirrorKey,"{}")) }.getOrDefault(JSONObject())
        val remote = list(bucket,user,secret)
        val server = linkedMapOf<String,JSONObject>()
        for (index in 0 until remote.length()) {
            val row = remote.optJSONObject(index) ?: continue
            server[row.optString("id")] = row
        }
        val batch = JSONArray()
        val conflicts = JSONArray()
        for ((id,record) in local) {
            val previous = before.optJSONObject(id)
            val existing = server[id]
            val changed = previous == null || previous.optString("data") != record.toString()
            if (existing == null) {
                batch.put(JSONObject().put("id",id).put("revision",0).put("data",record).put("deleted",false))
            } else if (changed) {
                if (previous != null && previous.optLong("revision") != existing.optLong("revision")) {
                    conflicts.put(JSONObject().put("id",id).put("local",record).put("remote",existing))
                } else {
                    batch.put(JSONObject().put("id",id).put("revision",existing.optLong("revision")).put("data",record).put("deleted",false))
                }
            }
        }
        // Tombstones are only written for items previously synced and subsequently deleted locally.
        val known = before.keys()
        while (known.hasNext()) {
            val id = known.next()
            if (local.containsKey(id) || before.optJSONObject(id)?.optBoolean("deleted")==true) continue
            val existing = server[id] ?: continue
            val previous = before.optJSONObject(id) ?: continue
            if (existing.optLong("revision") == previous.optLong("revision") && !existing.optBoolean("deleted")) {
                batch.put(JSONObject().put("id",id).put("revision",existing.optLong("revision"))
                    .put("data",existing.optJSONObject("data") ?: JSONObject()).put("deleted",true))
            } else if (!existing.optBoolean("deleted")) {
                conflicts.put(JSONObject().put("id",id).put("remote",existing).put("reason","deleted while modified remotely"))
            }
        }
        if (batch.length()>0) {
            var start = 0
            while (start < batch.length()) {
                val slice = JSONArray()
                for (i in start until minOf(start+200,batch.length())) slice.put(batch.get(i))
                val result = JSONObject(request("/api/sync/$bucket/batch",user,secret,JSONObject().put("records",slice)))
                val failures = result.optJSONArray("conflicts") ?: JSONArray()
                for (i in 0 until failures.length()) conflicts.put(failures.get(i))
                start += 200
            }
        }
        val fresh = list(bucket,user,secret)
        val merged = linkedMapOf<String,JSONObject>()
        for ((id,item) in local) merged[id] = item
        val mirror = JSONObject()
        for (i in 0 until fresh.length()) {
            val record = fresh.optJSONObject(i) ?: continue
            val id = record.optString("id")
            val payload = record.optJSONObject("data") ?: JSONObject()
            val existingLocal = local[id]
            val previous = before.optJSONObject(id)
            val modifiedLocally = previous != null && existingLocal != null && previous.optString("data") != existingLocal.toString()
            if (record.optBoolean("deleted")) {
                if (!modifiedLocally) merged.remove(id)
            } else if (!modifiedLocally) {
                payload.put("_syncId",id)
                merged[id] = payload
            }
            mirror.put(id,JSONObject().put("revision",record.optLong("revision"))
                .put("data",payload.toString()).put("deleted",record.optBoolean("deleted")))
        }
        if (conflicts.length()>0) {
            val prior = runCatching { JSONArray(prefs.getString("sync_conflicts_$bucket","[]")) }.getOrDefault(JSONArray())
            for (i in 0 until conflicts.length()) prior.put(conflicts.get(i))
            prefs.edit().putString("sync_conflicts_$bucket",prior.toString()).apply()
            status = "Sync conflict(s) in $bucket; local backups retained"
        }
        val saved = JSONArray()
        merged.values.forEach { saved.put(it) }
        prefs.edit().putString(localKey,saved.toString()).putString(mirrorKey,mirror.toString()).apply()
    }

    private fun syncCalibration(context: Context,user: String,secret: String) {
        val prefs = context.getSharedPreferences("electrical_workbench_bender_calibration",Context.MODE_PRIVATE)
        val name = prefs.getString("name","").orEmpty()
        val model = prefs.getString("model","").orEmpty()
        if (name.isBlank() || model.isBlank()) return
        var id = prefs.getString("_syncId","").orEmpty()
        if (id.isBlank()) { id = newId();prefs.edit().putString("_syncId",id).apply() }
        val values = JSONObject()
        for (key in listOf("name","model","material","size","units","mark","stub","stock","first","second","deduct","gain")) {
            values.put(key,prefs.getString(key,"").orEmpty())
        }
        values.put("_syncId",id)
        val items = list("benderProfiles",user,secret)
        val found = (0 until items.length()).mapNotNull { items.optJSONObject(it) }.firstOrNull { it.optString("id")==id }
        val previousData = prefs.getString("_syncLastData","")
        val localChanged = values.toString()!=previousData
        if (found == null || localChanged) {
            val expected = found?.optLong("revision") ?: 0
            val previousVersion = prefs.getLong("_syncRevision",0)
            if (found != null && previousVersion != expected && previousVersion > 0) {
                status = "Bender profile edit conflict; local calibration kept"
                return
            }
            val batch = JSONObject().put("records",JSONArray().put(JSONObject()
                .put("id",id).put("revision",expected).put("data",values).put("deleted",false)))
            val result = JSONObject(request("/api/sync/benderProfiles/batch",user,secret,batch))
            val accepted = result.optJSONArray("accepted")
            if (accepted != null && accepted.length()>0) {
                prefs.edit().putLong("_syncRevision",accepted.getJSONObject(0).getLong("revision"))
                    .putString("_syncLastData",values.toString()).apply()
            }
        } else {
            prefs.edit().putLong("_syncRevision",found.optLong("revision")).apply()
        }
    }
}
