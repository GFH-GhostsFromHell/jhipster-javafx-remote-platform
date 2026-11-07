import Foundation
import CryptoKit
import SwiftUI

/// Signed-in, local-first data bridge between native iOS stores and PostgreSQL account records.
/// Server revisions prevent overwriting another device's edits.
@MainActor
final class AccountRecordSync: ObservableObject {
    static let shared = AccountRecordSync()
    @Published private(set) var status = "Not synchronized"
    @Published private(set) var isSyncing = false
    private var lastRun = Date.distantPast
    private var activeUser = ""

    private let categories = [
        "projects","materials","nameplates","commissioning","inspections",
        "hours","credentials","quizHistory","unsafeConditions","rubberGloves",
        "safetyMaintenance","stormStandDowns","safetyAssessments",
        "troubleshootingCases","benderProfiles"
    ]

    private struct Entry {
        let id: String
        let revision: Int
        let data: [String: Any]
        let deleted: Bool
    }

    private struct Mirror: Codable {
        let revision: Int
        let digest: String
        let deleted: Bool
    }

    private func canonical(_ object: [String: Any]) -> String {
        guard JSONSerialization.isValidJSONObject(object),
              let encoded = try? JSONSerialization.data(withJSONObject: object,
                  options: [.sortedKeys, .fragmentsAllowed]),
              let text = String(data: encoded, encoding: .utf8) else { return "" }
        return text
    }

    private func id(_ text: String) -> UUID {
        if let uuid = UUID(uuidString: text) { return uuid }
        let digest = SHA256.hash(data: Data(text.utf8))
        let bytes = Array(digest.prefix(16))
        let uuid = bytes.enumerated().map { i, byte in
            [4,6,8,10].contains(i) ? String(format: "-%02x", byte) :
                String(format: "%02x", byte)
        }.joined()
        return UUID(uuidString: uuid) ?? UUID()
    }

    private func dataArray(_ value: Any) -> [[String: Any]] {
        (value as? [[String: Any]]) ?? []
    }

    private func read(_ key: String) -> [[String: Any]] {
        guard let data = UserDefaults.standard.data(forKey: key),
              let parsed = try? JSONSerialization.jsonObject(with: data) else { return [] }
        return dataArray(parsed)
    }

    private func write(_ key: String, rows: [[String: Any]]) {
        guard JSONSerialization.isValidJSONObject(rows),
              let encoded = try? JSONSerialization.data(withJSONObject: rows, options: [.sortedKeys]) else { return }
        UserDefaults.standard.set(encoded, forKey: key)
        NotificationCenter.default.post(name: .ewbAccountSyncUpdated, object: key)
    }

    private func mirrorKey(_ user: String, _ category: String) -> String {
        "ewb.ios.sync.\(user).\(category).meta"
    }

    private func loadMirror(_ user: String, _ category: String) -> [String: Mirror] {
        guard let data = UserDefaults.standard.data(forKey: mirrorKey(user, category)),
              let value = try? JSONDecoder().decode([String: Mirror].self, from: data) else {
            return [:]
        }
        return value
    }

    private func saveMirror(_ user: String, _ category: String, _ value: [String: Mirror]) {
        if let data = try? JSONEncoder().encode(value) {
            UserDefaults.standard.set(data, forKey: mirrorKey(user,category))
        }
    }

    private func parseEntries(_ data: Data) throws -> [Entry] {
        let value = try JSONSerialization.jsonObject(with: data)
        guard let records = value as? [[String: Any]] else {
            throw URLError(.cannotParseResponse)
        }
        return records.compactMap {
            guard let id = $0["id"] as? String, let revision = $0["revision"] as? Int else {
                return nil
            }
            return Entry(id: id, revision: revision, data: $0["data"] as? [String: Any] ?? [:],
                         deleted: $0["deleted"] as? Bool ?? false)
        }
    }

    /// Conflict backup is retained locally, with both versions, for manual export or review.
    private func backup(_ user: String, _ bucket: String, _ object: [String: Any]) {
        let key = "ewb.ios.sync.\(user).conflicts"
        var previous = read(key)
        var copy = object
        copy["bucket"] = bucket
        copy["time"] = ISO8601DateFormatter().string(from: .now)
        previous.append(copy)
        write(key,rows: Array(previous.suffix(100)))
    }

    func syncIfDue(account: CloudAccountSession, force: Bool = false) async {
        guard account.signedIn, !account.isOffline, !isSyncing,
              let user = account.identity?.username else { return }
        guard force || user != activeUser || Date().timeIntervalSince(lastRun) >= 300 else { return }
        activeUser = user
        isSyncing = true
        status = "Synchronizing saved field records…"
        defer { isSyncing = false }
        var success = 0
        var failed = 0
        for bucket in categories {
            guard account.identity?.username == user, !account.isOffline else { break }
            do {
                try await syncBucket(bucket, user:user, account:account)
                success += 1
            } catch {
                failed += 1
            }
        }
        lastRun = .now
        status = failed == 0
            ? "Account sync up to date (\(success) collections)."
            : "Offline changes retained. \(success) synced; \(failed) unavailable or conflicted."
    }

    private func syncBucket(_ bucket: String, user: String, account: CloudAccountSession) async throws {
        let key = storageKey(bucket,user:user)
        let local = loadNative(bucket:bucket,user:user,key:key)
        let mirror = loadMirror(user,bucket)
        let remote = try parseEntries(await account.api("/api/sync/\(bucket)"))
        let server = Dictionary(uniqueKeysWithValues: remote.map { ($0.id,$0) })
        var queued: [[String: Any]] = []
        var dirty = Set<String>()
        var conflicting = Set<String>()
        let localMap = Dictionary(uniqueKeysWithValues: local.compactMap { item -> (String,[String:Any])? in
            guard let id = item["_syncId"] as? String else { return nil }
            return (id,item)
        })

        for item in local {
            guard let id = item["_syncId"] as? String else { continue }
            let changed = mirror[id] == nil || mirror[id]?.digest != canonical(item)
            guard changed else { continue }
            if let current = server[id], mirror[id] == nil,
               canonical(item) != canonical(current.data) {
                backup(user,bucket,["id":id,"local":item,"remote":current.data,
                                    "reason":"First-sync difference"])
                conflicting.insert(id)
                continue
            }
            if let current = server[id], let base = mirror[id],
               current.revision != base.revision {
                backup(user,bucket,["id":id,"local":item,"remote":current.data,
                                    "reason":"Concurrent edit"])
                dirty.insert(id)
                conflicting.insert(id)
                continue
            }
            if server[id] == nil || mirror[id] != nil {
                queued.append(["id":id,
                               "revision":server[id]?.revision ?? 0,
                               "data":item,
                               "deleted":false])
                dirty.insert(id)
            }
        }
        // Deletions are only recognized for records in the successful prior mirror.
        for (id, prior) in mirror where localMap[id] == nil && !prior.deleted {
            guard let current = server[id], !current.deleted else { continue }
            if current.revision != prior.revision {
                backup(user,bucket,["id":id,"remote":current.data,
                                    "reason":"Record deleted locally but changed on another device"])
                conflicting.insert(id)
                continue
            }
            queued.append(["id":id,"revision":current.revision,
                           "data":current.data,"deleted":true])
            dirty.insert(id)
        }

        for start in stride(from:0,to:queued.count,by:200) {
            let payload = ["records":Array(queued[start..<min(start+200,queued.count)])]
            let bytes = try JSONSerialization.data(withJSONObject:payload)
            let response = try await account.api("/api/sync/\(bucket)/batch",
                                                 method:"POST",json:bytes)
            if let object = try JSONSerialization.jsonObject(with:response) as? [String:Any],
               let failures = object["conflicts"] as? [[String:Any]] {
                for failure in failures {
                    backup(user,bucket, failure)
                    if let id = failure["id"] as? String {
                        dirty.insert(id)
                        conflicting.insert(id)
                    }
                }
            }
        }

        let fresh = try parseEntries(await account.api("/api/sync/\(bucket)"))
        var localByID: [String:[String:Any]] = [:]
        for item in local {
            if let id = item["_syncId"] as? String { localByID[id] = item }
        }
        var merged: [[String:Any]] = []
        var nextMirror: [String:Mirror] = [:]
        for entry in fresh {
            let current = localByID[entry.id]
            let keepLocal = conflicting.contains(entry.id) && current != nil
            if !entry.deleted {
                var payload = keepLocal ? (current ?? entry.data) : entry.data
                payload["_syncId"] = entry.id
                merged.append(payload)
            } else if keepLocal, var preserved = current {
                preserved["_syncId"] = entry.id
                merged.append(preserved)
            }
            var normalized = entry.data
            normalized["_syncId"] = entry.id
            nextMirror[entry.id] = keepLocal
                ? (mirror[entry.id] ?? Mirror(revision:entry.revision,
                     digest:canonical(current ?? normalized),deleted:entry.deleted))
                : Mirror(revision:entry.revision,digest:canonical(normalized),deleted:entry.deleted)
        }
        let existingIDs = Set(fresh.map { $0.id })
        for item in local {
            guard let id = item["_syncId"] as? String, !existingIDs.contains(id) else { continue }
            merged.append(item)
        }

        if JSONSerialization.isValidJSONObject(merged) {
            let old = local.map { canonical($0) }.joined(separator:"\n")
            let new = merged.map { canonical($0) }.joined(separator:"\n")
            if old != new {
                saveNative(bucket:bucket,user:user,key:key,rows:merged)
            }
        }
        // Compare against the shape persisted by this native client, not the
        // source platform's payload, to prevent repeated false local edits.
        let savedRows = loadNative(bucket:bucket,user:user,key:key)
        let savedMap = Dictionary(uniqueKeysWithValues: savedRows.compactMap { row -> (String,[String:Any])? in
            guard let id = row["_syncId"] as? String else { return nil }
            return (id,row)
        })
        for (id, value) in nextMirror where !conflicting.contains(id) {
            if let normalized = savedMap[id] {
                nextMirror[id] = Mirror(revision:value.revision,
                    digest:canonical(normalized),deleted:value.deleted)
            }
        }
        saveMirror(user,bucket,nextMirror)
    }

    private func storageKey(_ bucket: String, user: String) -> String {
        if bucket == "troubleshootingCases" { return "ewb.ios.troubleshooting.cases.\(user)" }
        if bucket == "benderProfiles" { return "ewb.ios.calibration.sync.\(user)" }
        if bucket == "quizHistory" { return "ewb.ios.quiz.sync.\(user)" }
        if ["unsafeConditions","rubberGloves","safetyMaintenance","stormStandDowns","safetyAssessments"].contains(bucket) {
            return "ewb.ios.safety.\(user)"
        }
        return "ewb.ios.workrecords.\(user)"
    }

    private func loadNative(bucket: String, user: String, key: String) -> [[String:Any]] {
        if bucket == "benderProfiles" {
            return loadCalibration(user:user,key:key)
        }
        if bucket == "quizHistory" {
            return loadDailyQuiz(user:user,key:key)
        }
        let records = read(key)
        return records.compactMap { item in
            if key.contains(".workrecords.") {
                guard item["category"] as? String == bucket else { return nil }
            } else if key.contains(".safety.") {
                guard categoryForSafety(item["kind"] as? String ?? "") == bucket else { return nil }
            }
            var normalized = item
            normalized["_syncId"] = item["syncId"] as? String ?? item["_syncId"] as? String ?? item["id"] as? String
            return normalized
        }
    }

    private func saveNative(bucket: String,user: String,key: String,rows: [[String:Any]]) {
        if bucket == "benderProfiles" {
            applyCalibration(user:user,key:key,rows:rows)
            return
        }
        if bucket == "quizHistory" {
            applyDailyQuiz(user:user,key:key,rows:rows)
            return
        }
        if bucket == "troubleshootingCases" {
            let converted = rows.map { row -> [String:Any] in
                let sid = row["_syncId"] as? String ?? row["id"] as? String ?? UUID().uuidString
                return [
                    "id":id(sid).uuidString,"syncId":sid,
                    "topic":row["topic"] as? String ?? row["title"] as? String ?? "Troubleshooting",
                    "asset":row["asset"] as? String ?? row["equipment"] as? String ?? "",
                    "notes":row["notes"] as? String ?? "",
                    "completed":row["completed"] as? Int ?? 0,
                    "total":row["total"] as? Int ?? 0,
                    "createdAt":dateValue(row["createdAt"] ?? row["created"])
                ]
            }
            write(key,rows:converted)
            return
        }
        var existing = read(key)
        if key.contains(".workrecords.") {
            existing.removeAll { $0["category"] as? String == bucket }
            for row in rows {
                let syncId = row["_syncId"] as? String ?? UUID().uuidString
                let record = [
                    "id":id(syncId).uuidString,
                    "syncId":syncId,
                    "category":bucket,
                    "title":row["title"] as? String ?? row["name"] as? String ??
                        row["employer"] as? String ?? row["equipment"] as? String ?? bucket,
                    "notes":row["notes"] as? String ?? row["deficiencies"] as? String ?? "",
                    "amount":row["amount"] as? Double ?? row["hours"] as? Double ?? 0,
                    "created":dateValue(row["created"] ?? row["date"])
                ] as [String:Any]
                existing.append(record)
            }
        } else {
            existing.removeAll { categoryForSafety($0["kind"] as? String ?? "") == bucket }
            for row in rows {
                let syncId = row["_syncId"] as? String ?? UUID().uuidString
                existing.append([
                    "id":id(syncId).uuidString,
                    "syncId":syncId,
                    "kind":row["kind"] as? String ?? bucket,
                    "asset":row["asset"] as? String ?? row["site"] as? String ?? "",
                    "notes":row["notes"] as? String ?? "",
                    "date":dateValue(row["date"])
                ])
            }
        }
        write(key,rows:existing)
    }

    private func dateValue(_ input: Any?) -> Double {
        if let number = input as? NSNumber { return number.doubleValue }
        if let text = input as? String {
            if let date = ISO8601DateFormatter().date(from:text) {
                return date.timeIntervalSinceReferenceDate
            }
            let formatter=DateFormatter();formatter.dateFormat="yyyy-MM-dd"
            if let date = formatter.date(from:text) { return date.timeIntervalSinceReferenceDate }
        }
        return Date().timeIntervalSinceReferenceDate
    }

    private func categoryForSafety(_ kind: String) -> String {
        let lower = kind.lowercased()
        if lower.contains("thunder") || lower.contains("lightning") { return "stormStandDowns" }
        if lower.contains("ppe") || lower.contains("arc") { return "safetyAssessments" }
        if lower.contains("glove") { return "rubberGloves" }
        if lower.contains("maintenance") { return "safetyMaintenance" }
        return "unsafeConditions"
    }

    private func loadCalibration(user: String,key:String) -> [[String:Any]] {
        let defaults = UserDefaults.standard
        let owner = defaults.string(forKey:"ewb.ios.calibration.owner")
        guard owner == nil || owner == user else { return read(key) }
        let name = defaults.string(forKey:"ewb.ios.calibration.name") ?? ""
        let model = defaults.string(forKey:"ewb.ios.calibration.model") ?? ""
        if name.isEmpty || model.isEmpty { return read(key) }
        defaults.set(user,forKey:"ewb.ios.calibration.owner")
        let savedID = defaults.string(forKey:"ewb.ios.calibration.profileID") ?? UUID().uuidString
        defaults.set(savedID,forKey:"ewb.ios.calibration.profileID")
        let item: [String:Any] = [
            "_syncId":savedID,"id":savedID,"name":name,"model":model,
            "material":defaults.string(forKey:"ewb.ios.calibration.material") ?? "EMT",
            "size":defaults.string(forKey:"ewb.ios.calibration.size") ?? "",
            "units":"mm",
            "deduct":defaults.double(forKey:"ewb.ios.calibration.deduct.mm"),
            "gain":defaults.double(forKey:"ewb.ios.calibration.gain.mm")
        ]
        var all=read(key)
        all.removeAll { ($0["_syncId"] as? String) == savedID }
        all.append(item)
        return all
    }

    private func applyCalibration(user:String,key:String,rows:[[String:Any]]) {
        write(key,rows:rows)
        let defaults = UserDefaults.standard
        guard defaults.string(forKey:"ewb.ios.calibration.owner") == nil ||
              defaults.string(forKey:"ewb.ios.calibration.owner") == user else { return }
        guard let match = rows.first(where:{
            ($0["_syncId"] as? String) == defaults.string(forKey:"ewb.ios.calibration.profileID")
        }) ?? (defaults.string(forKey:"ewb.ios.calibration.name") == nil ? rows.first : nil) else {
            return
        }
        defaults.set(user,forKey:"ewb.ios.calibration.owner")
        defaults.set(match["_syncId"] as? String,forKey:"ewb.ios.calibration.profileID")
        defaults.set(match["name"] as? String ?? "",forKey:"ewb.ios.calibration.name")
        defaults.set(match["model"] as? String ?? "",forKey:"ewb.ios.calibration.model")
        defaults.set(match["size"] as? String ?? "",forKey:"ewb.ios.calibration.size")
        defaults.set(match["material"] as? String ?? "EMT",forKey:"ewb.ios.calibration.material")
        let multiplier = (match["units"] as? String) == "in" ? 25.4 : 1.0
        if let raw = match["deduct"] as? NSNumber {
            defaults.set(raw.doubleValue * multiplier,forKey:"ewb.ios.calibration.deduct.mm")
        } else if let text = match["deduct"] as? String, let v = Double(text) {
            defaults.set(v * multiplier,forKey:"ewb.ios.calibration.deduct.mm")
        }
        if let raw = match["gain"] as? NSNumber {
            defaults.set(raw.doubleValue * multiplier,forKey:"ewb.ios.calibration.gain.mm")
        } else if let text = match["gain"] as? String, let v = Double(text) {
            defaults.set(v * multiplier,forKey:"ewb.ios.calibration.gain.mm")
        }
    }

    private func loadDailyQuiz(user:String,key:String) -> [[String:Any]] {
        let formatter = DateFormatter();formatter.dateFormat="yyyy-MM-dd"
        let day=formatter.string(from:.now)
        let answers=UserDefaults.standard.dictionary(forKey:"ewb.ios.quiz.\(day)") as? [String:Int] ?? [:]
        var existing=read(key)
        let today="day-\(day)"
        existing.removeAll { ($0["_syncId"] as? String) == today }
        existing.append(["id":today,"_syncId":today,"date":day,
                 "answers":answers,"category":"quizHistory"])
        return existing
    }

    private func applyDailyQuiz(user:String,key:String,rows:[[String:Any]]) {
        write(key,rows:rows)
        let formatter=DateFormatter();formatter.dateFormat="yyyy-MM-dd"
        let today=formatter.string(from:.now)
        guard let remote=rows.first(where:{$0["date"] as? String == today}),
              let answers=remote["answers"] as? [String:Int] else {return}
        UserDefaults.standard.set(answers,forKey:"ewb.ios.quiz.\(today)")
    }
}
extension Notification.Name {
    static let ewbAccountSyncUpdated = Notification.Name("ewb.account.sync.updated")
    static let ewbLocalRecordChanged = Notification.Name("ewb.local.record.changed")
}
