package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Local/offline persistence for apprentice, project, field and safety records. Records are intentionally per-user. */
public final class LocalWorkspaceStore {
    private final ObjectMapper json;
    private final Path file;
    private final String username;

    private static final String[] COLLECTIONS = new String[]{
        "projects","nameplates","commissioning","inspections","hours","credentials","quizHistory","materials",
        "safetyAssessments","unsafeConditions","rubberGloves","safetyMaintenance","stormStandDowns",
        "guidedSolutions","practiceDrafts","designReports","panelSchedules","motorDesigns","transformerDesigns",
        "estimates","calculatorFavorites","recentCalculations","customPresets"
    };

    public LocalWorkspaceStore(ObjectMapper json, String username) {
        this.json = json;
        this.username = username == null || username.isBlank() ? "default" : username;
        String safe = this.username.replaceAll("[^A-Za-z0-9._-]", "_");
        Path dir = Path.of(System.getProperty("user.home"), ".electrical-workbench");
        this.file = dir.resolve("workspace-" + safe + ".json");
    }

    public synchronized ObjectNode load() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) return blank();
            JsonNode node = json.readTree(Files.readString(file));
            if (node instanceof ObjectNode obj) return normalize(obj);
        } catch (Exception ignored) {}
        return blank();
    }

    public synchronized void append(String collection, ObjectNode record) {
        ObjectNode root = load();
        ArrayNode arr = ensure(root, collection);
        if (!record.hasNonNull("_syncId")) record.put("_syncId",java.util.UUID.randomUUID().toString());
        arr.add(record);
        save(root);
        DesktopAccountSync.schedule(username);
    }

    public synchronized ArrayNode records(String collection) {
        ObjectNode root = load();
        return ensure(root, collection).deepCopy();
    }

    public synchronized void replace(String collection, ArrayNode records) {
        ObjectNode root = load();
        root.set(collection, records);
        save(root);
        DesktopAccountSync.schedule(username);
    }

    public Path file() { return file; }
    public String username() { return username; }
    public static String[] synchronizedCollections() { return COLLECTIONS.clone(); }

    public synchronized ObjectNode syncMeta(String collection) {
        JsonNode meta = load().path("_sync_meta").path(collection);
        return meta instanceof ObjectNode node ? node.deepCopy() : json.createObjectNode();
    }

    public synchronized void applyAccountSync(String collection, ArrayNode records, ObjectNode metadata, ArrayNode conflicts) {
        ObjectNode root = load();
        root.set(collection,records);
        ObjectNode all = root.withObject("/_sync_meta");
        all.set(collection,metadata);
        if (conflicts != null && !conflicts.isEmpty()) {
            ArrayNode stored = ensure(root, "_sync_conflicts_" + collection);
            for (JsonNode item : conflicts) stored.add(item.deepCopy());
            while (stored.size() > 100) stored.remove(0);
        }
        save(root);
    }

    private void save(ObjectNode root) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save local workspace: " + ex.getMessage(), ex);
        }
    }

    private ObjectNode blank() {
        ObjectNode root = json.createObjectNode();
        for (String name : COLLECTIONS) root.set(name, json.createArrayNode());
        return root;
    }

    private ObjectNode normalize(ObjectNode root) {
        for (String name : COLLECTIONS) ensure(root, name);
        return root;
    }

    private ArrayNode ensure(ObjectNode root, String name) {
        JsonNode node = root.get(name);
        if (node instanceof ArrayNode arr) return arr;
        ArrayNode arr = json.createArrayNode();
        root.set(name, arr);
        return arr;
    }
}
