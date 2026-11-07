package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Password never written to disk. Offline saves remain in LocalWorkspaceStore.
 * Server revisions are compare-and-swap; conflicting local edits are backed up.
 */
public final class DesktopAccountSync {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final PortalClient PORTAL = new PortalClient();
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task,"ewb-account-sync");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private record Session(String user,String password) {}
    private static volatile Session session;
    private static volatile String status = "Sign in to synchronize saved field records";

    static {
        WORKER.scheduleWithFixedDelay(DesktopAccountSync::run,5,5,TimeUnit.MINUTES);
    }

    private DesktopAccountSync() {}
    public static String status() { return status; }

    public static void configure(String username, String password) {
        session = new Session(username,password);
        status = "Account connected · field sync queued";
        schedule(username);
    }

    public static void disconnect() {
        session = null;
        status = "Signed out · offline records retained";
    }

    public static void schedule(String username) {
        Session active = session;
        if (active == null || !active.user().equals(username)) return;
        WORKER.execute(DesktopAccountSync::run);
    }

    public static void run() {
        Session active = session;
        if (active == null || !RUNNING.compareAndSet(false,true)) return;
        try {
            LocalWorkspaceStore store = new LocalWorkspaceStore(JSON,active.user());
            int updated=0,failed=0;
            for (String collection : LocalWorkspaceStore.synchronizedCollections()) {
                if (session != active) break;
                try {
                    syncOne(store,collection,active);
                    updated++;
                } catch(Exception ex) {
                    failed++;
                    status = "Sync incomplete · local records retained ("+collection+"): "+ex.getMessage();
                }
            }
            if (failed==0) status = "Synced "+updated+" account collections";
        } finally {
            RUNNING.set(false);
        }
    }

    private static void syncOne(LocalWorkspaceStore store, String bucket, Session active) throws Exception {
        ArrayNode originals = store.records(bucket);
        ArrayNode local = JSON.createArrayNode();
        boolean idsAdded = false;
        for (JsonNode node : originals) {
            if (!(node instanceof ObjectNode record)) continue;
            ObjectNode copy=record.deepCopy();
            if (copy.path("_syncId").asText("").isBlank()) {
                copy.put("_syncId",UUID.randomUUID().toString());
                idsAdded = true;
            }
            local.add(copy);
        }
        ObjectNode mirror = store.syncMeta(bucket);
        if (idsAdded) store.applyAccountSync(bucket,local,mirror,JSON.createArrayNode());
        ArrayNode remote = (ArrayNode) JSON.readTree(PORTAL.accountSyncList(bucket,active.user(),active.password()));
        Map<String,ObjectNode> server = mapRecords(remote);
        Map<String,ObjectNode> localMap = mapLocal(local);
        ArrayNode writes = JSON.createArrayNode();
        ArrayNode backups = JSON.createArrayNode();
        Set<String> dirty = new HashSet<>();

        for (Map.Entry<String,ObjectNode> item : localMap.entrySet()) {
            String id=item.getKey();ObjectNode data=item.getValue();
            ObjectNode base = mirror.path(id) instanceof ObjectNode o ? o : null;
            ObjectNode found=server.get(id);
            boolean changed=base==null||!base.path("digest").asText("").equals(data.toString());
            if (!changed) continue;
            if (found!=null&&base!=null&&base.path("revision").asLong()!=found.path("revision").asLong()) {
                backups.add(conflict(id,data,found,"Concurrent modification"));
                dirty.add(id);
                continue;
            }
            if (found==null||base!=null) {
                writes.add(write(id,found==null?0:found.path("revision").asLong(),data,false));
                dirty.add(id);
            }
        }
        Iterator<String> known = mirror.fieldNames();
        while(known.hasNext()) {
            String id=known.next();
            if(localMap.containsKey(id)||mirror.path(id).path("deleted").asBoolean(false))continue;
            ObjectNode current=server.get(id);
            if(current==null||current.path("deleted").asBoolean())continue;
            if(mirror.path(id).path("revision").asLong()==current.path("revision").asLong()) {
                writes.add(write(id,current.path("revision").asLong(),
                    current.path("data") instanceof ObjectNode o ? o : JSON.createObjectNode(),true));
                dirty.add(id);
            } else {
                backups.add(conflict(id,null,current,"Deleted locally, changed on another device"));
            }
        }
        for(int start=0;start<writes.size();start+=200) {
            ArrayNode page=JSON.createArrayNode();
            for(int j=start;j<Math.min(start+200,writes.size());j++)page.add(writes.get(j));
            ObjectNode req=JSON.createObjectNode();req.set("records",page);
            JsonNode result=JSON.readTree(PORTAL.accountSyncBatch(bucket,active.user(),active.password(),req.toString()));
            for(JsonNode conflict:result.path("conflicts"))backups.add(conflict.deepCopy());
        }
        ArrayNode fresh=(ArrayNode)JSON.readTree(PORTAL.accountSyncList(bucket,active.user(),active.password()));
        ArrayNode merged=JSON.createArrayNode();
        ObjectNode nextMeta=JSON.createObjectNode();
        Set<String> seen=new HashSet<>();

        for (JsonNode node:fresh) {
            if (!(node instanceof ObjectNode record))continue;
            String id=record.path("id").asText();seen.add(id);
            ObjectNode localData=localMap.get(id);
            ObjectNode previous=mirror.path(id) instanceof ObjectNode o ? o : null;
            boolean changedLocally=localData!=null&&previous!=null&&
                !previous.path("digest").asText("").equals(localData.toString());
            boolean keepLocal=changedLocally&&dirty.contains(id);
            if (!record.path("deleted").asBoolean()) {
                ObjectNode candidate;
                if(keepLocal)candidate=localData.deepCopy();
                else candidate=record.path("data") instanceof ObjectNode obj?obj.deepCopy():JSON.createObjectNode();
                candidate.put("_syncId",id);
                normalize(bucket,candidate);
                merged.add(candidate);
                ObjectNode m=JSON.createObjectNode();
                m.put("revision",keepLocal&&previous!=null?previous.path("revision").asLong():record.path("revision").asLong());
                m.put("digest",candidate.toString());
                m.put("deleted",false);
                nextMeta.set(id,m);
            } else if(keepLocal) {
                merged.add(localData.deepCopy());
                if(previous!=null)nextMeta.set(id,previous.deepCopy());
            } else {
                ObjectNode m=JSON.createObjectNode();
                m.put("revision",record.path("revision").asLong());
                m.put("digest",record.path("data").toString());
                m.put("deleted",true);
                nextMeta.set(id,m);
            }
        }
        for (var localItem:localMap.entrySet()) if(!seen.contains(localItem.getKey()))merged.add(localItem.getValue().deepCopy());
        store.applyAccountSync(bucket,merged,nextMeta,backups);
    }

    private static Map<String,ObjectNode> mapRecords(ArrayNode array) {
        Map<String,ObjectNode> map=new LinkedHashMap<>();
        for(JsonNode node:array)if(node instanceof ObjectNode obj)map.put(obj.path("id").asText(),obj);
        return map;
    }
    private static Map<String,ObjectNode> mapLocal(ArrayNode array) {
        Map<String,ObjectNode> map=new LinkedHashMap<>();
        for(JsonNode node:array)if(node instanceof ObjectNode obj)map.put(obj.path("_syncId").asText(),obj);
        return map;
    }
    private static ObjectNode write(String id,long revision,ObjectNode data,boolean deleted) {
        ObjectNode node=JSON.createObjectNode();
        node.put("id",id);node.put("revision",revision);
        node.set("data",data);node.put("deleted",deleted);
        return node;
    }
    private static ObjectNode conflict(String id,ObjectNode local,ObjectNode remote,String reason) {
        ObjectNode node=JSON.createObjectNode();
        node.put("id",id);node.put("reason",reason);
        if(local!=null)node.set("local",local.deepCopy());
        if(remote!=null)node.set("remote",remote.deepCopy());
        return node;
    }
    private static void normalize(String bucket,ObjectNode data) {
        if("hours".equals(bucket)&&!data.has("hours")&&data.has("amount"))
            data.set("hours",data.path("amount").deepCopy());
        if(Set.of("projects","credentials").contains(bucket)&&!data.has("name")&&data.has("title"))
            data.set("name",data.path("title").deepCopy());
        if("nameplates".equals(bucket)&&!data.has("equipment")&&data.has("title"))
            data.set("equipment",data.path("title").deepCopy());
    }
}
