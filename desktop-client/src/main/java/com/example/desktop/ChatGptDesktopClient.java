package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.awt.Desktop;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * OpenAI Sign in with ChatGPT + ChatGPT-plan Responses API support for the
 * locally installed desktop client. Credentials are never sent to the
 * Electrical Workbench portal.
 */
final class ChatGptDesktopClient {
    static final URI MANAGE_USAGE = URI.create("https://chatgpt.com/settings/usage");

    private static final String AUTH = "https://auth.openai.com/api/accounts/authorize";
    private static final String TOKEN = "https://auth.openai.com/api/accounts/oauth/token";
    private static final String REVOKE = "https://auth.openai.com/api/accounts/oauth/revoke";
    private static final String JWKS = "https://auth.openai.com/.well-known/jwks.json";
    private static final String ISSUER = "https://auth.openai.com";
    private static final String RESOURCE = "https://api.openai.com/v1";
    private static final String MODELS = "https://api.openai.com/v1/models";
    private static final String RESPONSES = "https://api.openai.com/v1/responses";
    private static final String SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";
    private static final String DYNAMIC_CLIENT = "dynamic_agent_client";
    private static final String APP_NAME = "Electrical Workbench";

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final CredentialStore store;

    ChatGptDesktopClient(String username) {
        this.store = new CredentialStore(username, json);
    }

    record Profile(
        String clientId,
        String hostId,
        String subject,
        String email,
        String name,
        String idToken,
        String accessToken,
        String refreshToken,
        Set<String> scopes,
        long expiresAtMs
    ) {
        boolean sharingEnabled() {
            return accessToken != null && !accessToken.isBlank() && scopes != null && scopes.contains("chatgpt.tokens.use.direct");
        }
    }

    record Model(String slug, String displayName) {
        @Override public String toString() { return displayName == null || displayName.isBlank() ? slug : displayName; }
    }

    record ChatLine(String role, String text) {}

    Profile profile() { return store.loadProfile(); }

    Profile connect(boolean forceNewRegistration, Consumer<String> onStatus) throws Exception {
        Profile saved = forceNewRegistration ? null : store.loadProfile();
        try (LoopbackCallback callback = new LoopbackCallback()) {
            String state = randomUrlSafe(32);
            String nonce = randomUrlSafe(32);
            String verifier = randomUrlSafe(64);
            String challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            String hostId = store.hostId();
            String requestedClient = saved == null ? DYNAMIC_CLIENT : saved.clientId();

            Map<String,String> params = new LinkedHashMap<>();
            params.put("client_id", requestedClient);
            params.put("ext_agent_host_id", hostId);
            params.put("response_type", "code");
            params.put("redirect_uri", callback.redirectUri());
            params.put("scope", SCOPES);
            params.put("resource", RESOURCE);
            params.put("state", state);
            params.put("nonce", nonce);
            params.put("code_challenge_method", "S256");
            params.put("code_challenge", challenge);
            if (saved == null) params.put("agent_name_hint", APP_NAME);
            if (saved != null && saved.idToken() != null && !saved.idToken().isBlank()) params.put("id_token_hint", saved.idToken());
            if (saved != null && saved.email() != null && !saved.email().isBlank()) params.put("login_hint", saved.email());

            URI authorize = URI.create(AUTH + "?" + form(params));
            onStatus.accept("Waiting for ChatGPT authorization in your browser…");
            openBrowser(authorize);

            Map<String,String> returned = callback.await();
            if (!Objects.equals(state, returned.get("state"))) throw new SecurityException("ChatGPT sign-in returned an invalid state value.");
            if (returned.containsKey("error")) {
                String err = returned.get("error");
                throw new SecurityException("access_denied".equals(err) ? "ChatGPT authorization was cancelled or not approved." : "ChatGPT authorization failed: " + err);
            }
            String code = required(returned.get("code"), "ChatGPT did not return an authorization code.");
            String issuedClient = returned.get("client_id");
            if (issuedClient == null || issuedClient.isBlank()) issuedClient = saved == null ? null : saved.clientId();
            issuedClient = required(issuedClient, "ChatGPT registration did not return an issued client ID.");
            if (saved != null && !Objects.equals(saved.clientId(), issuedClient)) throw new SecurityException("ChatGPT returned a different client registration than expected.");

            onStatus.accept("Verifying your ChatGPT connection…");
            JsonNode token = postForm(TOKEN, Map.of(
                "grant_type", "authorization_code",
                "client_id", issuedClient,
                "code", code,
                "code_verifier", verifier,
                "redirect_uri", callback.redirectUri(),
                "resource", RESOURCE
            ));
            String idToken = text(token, "id_token");
            if (idToken == null) throw new SecurityException("ChatGPT did not return a verifiable identity token.");
            Identity identity = verifyIdToken(idToken, issuedClient, nonce);
            if (saved != null && saved.subject() != null && !saved.subject().isBlank() && !Objects.equals(saved.subject(), identity.subject())) {
                throw new SecurityException("The authorized ChatGPT account did not match the saved connection.");
            }

            String scopeText = token.path("scope").asText(returned.getOrDefault("scope", ""));
            Set<String> scopes = splitScopes(scopeText);
            String access = text(token, "access_token");
            String refresh = text(token, "refresh_token");
            long expiresIn = token.path("expires_in").asLong(3600L);
            Profile profile = new Profile(
                issuedClient, hostId, identity.subject(), identity.email(), identity.name(),
                idToken, access, refresh, scopes, System.currentTimeMillis() + expiresIn * 1000L
            );
            store.saveProfile(profile);
            return profile;
        }
    }

    List<Model> listModels() throws Exception {
        Profile profile = ensureAccess();
        HttpRequest request = HttpRequest.newBuilder(URI.create(MODELS))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + profile.accessToken())
            .header("Accept", "application/json")
            .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        ensureOk(response.statusCode(), response.body());
        JsonNode root = json.readTree(response.body());
        List<Model> out = new ArrayList<>();
        for (JsonNode item : root.path("models")) {
            String visibility = item.path("visibility").asText("");
            String slug = item.path("slug").asText("");
            if (!slug.isBlank() && (visibility.isBlank() || "list".equals(visibility))) {
                String display = item.path("display_name").asText(slug);
                out.add(new Model(slug, display));
            }
        }
        return out;
    }

    String streamResponse(String model, List<ChatLine> history, Consumer<String> onDelta) throws Exception {
        Profile profile = ensureAccess();
        ObjectNode body = json.createObjectNode();
        body.put("model", model);
        body.put("instructions", "You are ChatGPT inside Electrical Workbench. Answer normally. When discussing electrical code, safety, energized work, protection settings, or field procedures, clearly distinguish general guidance from authoritative requirements and encourage verification against the applicable CEC/OESC, manufacturer instructions, engineering documents, and site procedures.");
        ArrayNode input = body.putArray("input");
        for (ChatLine line : history) {
            ObjectNode item = input.addObject();
            item.put("role", line.role());
            item.put("content", line.text());
        }
        body.put("store", false);
        body.put("stream", true);

        HttpRequest request = HttpRequest.newBuilder(URI.create(RESPONSES))
            .timeout(Duration.ofMinutes(4))
            .header("Authorization", "Bearer " + profile.accessToken())
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();
        HttpResponse<Stream<String>> response = http.send(request, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            String error = response.body().reduce("", (a,b) -> a + b + "\n");
            throw new IllegalStateException(openAiError(response.statusCode(), error));
        }

        StringBuilder collected = new StringBuilder();
        boolean[] completed = {false};
        try (Stream<String> lines = response.body()) {
            lines.forEach(line -> {
                if (!line.startsWith("data:")) return;
                String data = line.substring(5).trim();
                if (data.isBlank() || "[DONE]".equals(data)) return;
                try {
                    JsonNode event = json.readTree(data);
                    switch (event.path("type").asText()) {
                        case "response.output_text.delta" -> {
                            String delta = event.path("delta").asText("");
                            if (!delta.isEmpty()) { collected.append(delta); onDelta.accept(delta); }
                        }
                        case "response.completed" -> {
                            completed[0] = true;
                            if (collected.isEmpty()) collected.append(extractCompletedText(event.path("response")));
                        }
                        case "response.failed" -> {
                            JsonNode err = event.path("response").path("error");
                            throw new StreamFailure(shareErrorMessage(err.path("code").asText(""), err.path("message").asText("")));
                        }
                        case "response.incomplete" -> throw new StreamFailure("ChatGPT ended the response before completion. Please try again.");
                        case "error" -> throw new StreamFailure(event.path("message").asText("ChatGPT returned an error."));
                        default -> { }
                    }
                } catch (StreamFailure failure) {
                    throw failure;
                } catch (Exception ignored) {
                    // Ignore non-JSON/unknown SSE lines while continuing the stream.
                }
            });
        } catch (StreamFailure failure) {
            throw new IllegalStateException(failure.getMessage());
        }
        if (!completed[0]) throw new IllegalStateException("ChatGPT stream ended before response.completed.");
        return collected.toString();
    }

    void disconnect() {
        Profile profile = store.loadProfile();
        if (profile == null) return;
        if (profile.refreshToken() != null && !profile.refreshToken().isBlank()) {
            try { postForm(REVOKE, Map.of("token", profile.refreshToken(), "token_type_hint", "refresh_token", "client_id", profile.clientId())); }
            catch (Exception ignored) { }
        }
        store.saveProfile(new Profile(profile.clientId(), profile.hostId(), profile.subject(), profile.email(), profile.name(), null, null, null, Set.of(), 0L));
    }

    private synchronized Profile ensureAccess() throws Exception {
        Profile profile = store.loadProfile();
        if (profile == null) throw new IllegalStateException("Connect a ChatGPT account first.");
        if (!profile.sharingEnabled()) throw new IllegalStateException("ChatGPT plan usage is not enabled for this connection. Reconnect and approve plan usage.");
        if (profile.expiresAtMs() > System.currentTimeMillis() + 60_000L) return profile;
        if (profile.refreshToken() == null || profile.refreshToken().isBlank()) throw new IllegalStateException("Your ChatGPT session expired. Reconnect the account.");
        JsonNode token = postForm(TOKEN, Map.of(
            "grant_type", "refresh_token",
            "client_id", profile.clientId(),
            "refresh_token", profile.refreshToken(),
            "resource", RESOURCE
        ));
        String access = required(text(token, "access_token"), "ChatGPT token refresh did not return an access token.");
        String refresh = Optional.ofNullable(text(token, "refresh_token")).orElse(profile.refreshToken());
        String newId = Optional.ofNullable(text(token, "id_token")).orElse(profile.idToken());
        String scopeText = token.path("scope").asText("");
        Set<String> scopes = scopeText.isBlank() ? profile.scopes() : splitScopes(scopeText);
        Profile refreshed = new Profile(profile.clientId(), profile.hostId(), profile.subject(), profile.email(), profile.name(), newId, access, refresh, scopes,
            System.currentTimeMillis() + token.path("expires_in").asLong(3600L) * 1000L);
        store.saveProfile(refreshed);
        return refreshed;
    }

    private Identity verifyIdToken(String token, String clientId, String nonce) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length != 3) throw new SecurityException("ChatGPT returned an invalid identity token.");
        JsonNode header = json.readTree(new String(base64UrlDecode(parts[0]), StandardCharsets.UTF_8));
        if (!"RS256".equals(header.path("alg").asText())) throw new SecurityException("Unsupported ChatGPT identity signature algorithm.");
        String kid = header.path("kid").asText();
        if (kid.isBlank()) throw new SecurityException("ChatGPT identity token did not identify a signing key.");

        HttpRequest req = HttpRequest.newBuilder(URI.create(JWKS)).timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        ensureOk(resp.statusCode(), resp.body());
        JsonNode key = null;
        for (JsonNode candidate : json.readTree(resp.body()).path("keys")) {
            if (kid.equals(candidate.path("kid").asText()) && "RSA".equals(candidate.path("kty").asText())) { key = candidate; break; }
        }
        if (key == null) throw new SecurityException("ChatGPT signing key was not found.");
        BigInteger modulus = new BigInteger(1, base64UrlDecode(key.path("n").asText()));
        BigInteger exponent = new BigInteger(1, base64UrlDecode(key.path("e").asText()));
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(publicKey);
        sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!sig.verify(base64UrlDecode(parts[2]))) throw new SecurityException("ChatGPT identity signature validation failed.");

        JsonNode claims = json.readTree(new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8));
        if (!ISSUER.equals(claims.path("iss").asText())) throw new SecurityException("ChatGPT identity issuer did not match OpenAI.");
        JsonNode aud = claims.get("aud");
        boolean audienceMatches = aud != null && ((aud.isTextual() && clientId.equals(aud.asText())) || (aud.isArray() && contains(aud, clientId)));
        if (!audienceMatches) throw new SecurityException("ChatGPT identity token was not issued for this app connection.");
        if (!nonce.equals(claims.path("nonce").asText())) throw new SecurityException("ChatGPT identity nonce did not match the sign-in attempt.");
        long now = System.currentTimeMillis() / 1000L;
        if (claims.path("exp").asLong(0L) < now - 5L) throw new SecurityException("ChatGPT identity token has expired.");
        long iat = claims.path("iat").asLong(0L);
        if (iat <= 0L || iat > now + 300L) throw new SecurityException("ChatGPT identity token has an invalid issue time.");
        String subject = claims.path("sub").asText();
        if (subject.isBlank()) throw new SecurityException("ChatGPT identity token did not contain a subject.");
        return new Identity(subject, claims.path("email").asText(""), claims.path("name").asText(""));
    }

    private static boolean contains(JsonNode array, String value) {
        for (JsonNode node : array) if (value.equals(node.asText())) return true;
        return false;
    }

    private JsonNode postForm(String url, Map<String,String> values) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(form(values)))
            .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        ensureOk(response.statusCode(), response.body());
        return response.body() == null || response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
    }

    private void ensureOk(int status, String body) {
        if (status >= 200 && status <= 299) return;
        throw new IllegalStateException(openAiError(status, body));
    }

    private String openAiError(int code, String body) {
        try {
            JsonNode error = json.readTree(body == null ? "{}" : body).path("error");
            String errorCode = error.path("code").asText("");
            String message = error.path("message").asText("");
            if ("subscription_sharing_usage_limit_exceeded".equals(errorCode) || "subscription_sharing_usage_unavailable".equals(errorCode)) return shareErrorMessage(errorCode, message);
            return message.isBlank() ? "OpenAI returned HTTP " + code + (errorCode.isBlank() ? "." : " (" + errorCode + ").") : message;
        } catch (Exception ignored) {
            return "OpenAI returned HTTP " + code + ".";
        }
    }

    private static String shareErrorMessage(String code, String message) {
        return switch (code) {
            case "subscription_sharing_usage_limit_exceeded" -> "Your ChatGPT app usage limit has been reached. Open Manage usage to review or change the limit.";
            case "subscription_sharing_usage_unavailable" -> "ChatGPT plan usage is currently unavailable for this account or app connection.";
            default -> message == null || message.isBlank() ? (code == null || code.isBlank() ? "ChatGPT request failed." : "ChatGPT request failed: " + code) : message;
        };
    }

    private static String extractCompletedText(JsonNode response) {
        StringBuilder text = new StringBuilder();
        for (JsonNode item : response.path("output")) {
            for (JsonNode part : item.path("content")) {
                if ("output_text".equals(part.path("type").asText())) text.append(part.path("text").asText(""));
            }
        }
        return text.toString();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalStateException(message);
        return value;
    }

    private static Set<String> splitScopes(String scopeText) {
        if (scopeText == null || scopeText.isBlank()) return Set.of();
        return new LinkedHashSet<>(Arrays.asList(scopeText.trim().split("\\s+")));
    }

    private static String form(Map<String,String> values) {
        StringJoiner out = new StringJoiner("&");
        values.forEach((key,value) -> out.add(enc(key) + "=" + enc(value)));
        return out.toString();
    }

    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private static void openBrowser(URI uri) throws Exception {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) throw new IllegalStateException("No system browser is available for ChatGPT sign-in.");
        Desktop.getDesktop().browse(uri);
    }

    private static String randomUrlSafe(int bytes) {
        byte[] data = new byte[bytes];
        new SecureRandom().nextBytes(data);
        return base64Url(data);
    }

    private static String base64Url(byte[] data) { return Base64.getUrlEncoder().withoutPadding().encodeToString(data); }
    private static byte[] base64UrlDecode(String text) { return Base64.getUrlDecoder().decode(text); }

    private record Identity(String subject, String email, String name) {}

    private static final class StreamFailure extends RuntimeException {
        StreamFailure(String message) { super(message); }
    }

    private static final class LoopbackCallback implements AutoCloseable {
        private final ServerSocket server;
        LoopbackCallback() throws IOException {
            server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            server.setSoTimeout(300_000);
        }
        String redirectUri() { return "http://127.0.0.1:" + server.getLocalPort() + "/auth/callback"; }
        Map<String,String> await() throws Exception {
            try (Socket socket = server.accept()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                String request = reader.readLine();
                if (request == null) throw new IllegalStateException("ChatGPT callback was empty.");
                while (true) { String line = reader.readLine(); if (line == null || line.isBlank()) break; }
                String[] pieces = request.split(" ");
                if (pieces.length < 2) throw new IllegalStateException("ChatGPT callback was malformed.");
                URI uri = URI.create("http://127.0.0.1" + pieces[1]);
                Map<String,String> params = parseQuery(uri.getRawQuery());
                String body = "<html><body style='font-family:sans-serif;background:#06111f;color:#f1f5f9;padding:32px'><h2>Electrical Workbench connected</h2><p>You can return to the desktop app now.</p></body></html>";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                String response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n";
                OutputStream out = socket.getOutputStream();
                out.write(response.getBytes(StandardCharsets.UTF_8)); out.write(bytes); out.flush();
                return params;
            }
        }
        @Override public void close() { try { server.close(); } catch (IOException ignored) {} }
    }

    private static Map<String,String> parseQuery(String query) {
        Map<String,String> out = new LinkedHashMap<>();
        if (query == null || query.isBlank()) return out;
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? part : part.substring(0,eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(eq < 0 ? "" : part.substring(eq+1), StandardCharsets.UTF_8);
            out.put(key,value);
        }
        return out;
    }

    private static final class CredentialStore {
        private final ObjectMapper json;
        private final Path base;
        private final Path profileFile;
        private final Path hostFile;
        private final Path fallbackKeyFile;

        CredentialStore(String username, ObjectMapper json) {
            this.json = json;
            String safe = username == null || username.isBlank() ? "default" : username.replaceAll("[^A-Za-z0-9._-]", "_");
            this.base = Path.of(System.getProperty("user.home"), ".electrical-workbench", "chatgpt");
            this.profileFile = base.resolve("profile-" + safe + ".dat");
            this.hostFile = base.resolve("host.id");
            this.fallbackKeyFile = base.resolve("local.key");
        }

        String hostId() {
            try {
                Files.createDirectories(base);
                if (Files.isRegularFile(hostFile)) {
                    String saved = Files.readString(hostFile).trim();
                    if (!saved.isBlank()) return saved;
                }
                String generated = "urn:uuid:" + UUID.randomUUID();
                Files.writeString(hostFile, generated, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                restrict(hostFile);
                return generated;
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to store the ChatGPT host ID: " + ex.getMessage(), ex);
            }
        }

        void saveProfile(Profile profile) {
            try {
                Files.createDirectories(base);
                ObjectNode root = json.createObjectNode();
                root.put("clientId", profile.clientId()); root.put("hostId", profile.hostId()); root.put("subject", profile.subject());
                root.put("email", profile.email()); root.put("name", profile.name());
                putNullable(root,"idToken",profile.idToken()); putNullable(root,"accessToken",profile.accessToken()); putNullable(root,"refreshToken",profile.refreshToken());
                ArrayNode scopes = root.putArray("scopes"); profile.scopes().forEach(scopes::add);
                root.put("expiresAtMs", profile.expiresAtMs());
                Files.writeString(profileFile, protect(root.toString()), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                restrict(profileFile);
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to securely save the ChatGPT connection: " + ex.getMessage(), ex);
            }
        }

        Profile loadProfile() {
            try {
                if (!Files.isRegularFile(profileFile)) return null;
                JsonNode root = json.readTree(unprotect(Files.readString(profileFile)));
                Set<String> scopes = new LinkedHashSet<>(); root.path("scopes").forEach(s -> scopes.add(s.asText()));
                return new Profile(root.path("clientId").asText(), root.path("hostId").asText(), root.path("subject").asText(), root.path("email").asText(""), root.path("name").asText(""),
                    nullable(root,"idToken"), nullable(root,"accessToken"), nullable(root,"refreshToken"), scopes, root.path("expiresAtMs").asLong(0L));
            } catch (Exception ex) {
                return null;
            }
        }

        private String protect(String plain) throws Exception {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return dpapi(plain, true);
            SecretKey key = fallbackKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key);
            String iv = Base64.getEncoder().encodeToString(cipher.getIV());
            String data = Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
            return "AESGCM:" + iv + ":" + data;
        }

        private String unprotect(String protectedText) throws Exception {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return dpapi(protectedText, false);
            String[] parts = protectedText.split(":",3); if (parts.length != 3 || !"AESGCM".equals(parts[0])) throw new SecurityException("Invalid local credential format");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, fallbackKey(), new GCMParameterSpec(128,Base64.getDecoder().decode(parts[1])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        }

        private String dpapi(String input, boolean protect) throws Exception {
            String script = protect
                ? "$s=[Console]::In.ReadToEnd();$b=[Text.Encoding]::UTF8.GetBytes($s);$e=[Security.Cryptography.ProtectedData]::Protect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Out.Write([Convert]::ToBase64String($e))"
                : "$s=[Console]::In.ReadToEnd();$b=[Convert]::FromBase64String($s.Trim());$e=[Security.Cryptography.ProtectedData]::Unprotect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Out.Write([Text.Encoding]::UTF8.GetString($e))";
            Process process = new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",script).redirectErrorStream(true).start();
            try (Writer writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) { writer.write(input); }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) throw new IllegalStateException("Windows credential protection failed.");
            return output.trim();
        }

        private SecretKey fallbackKey() throws Exception {
            Files.createDirectories(base);
            if (!Files.isRegularFile(fallbackKeyFile)) {
                KeyGenerator generator = KeyGenerator.getInstance("AES"); generator.init(256);
                Files.writeString(fallbackKeyFile, Base64.getEncoder().encodeToString(generator.generateKey().getEncoded()), StandardOpenOption.CREATE_NEW);
                restrict(fallbackKeyFile);
            }
            return new SecretKeySpec(Base64.getDecoder().decode(Files.readString(fallbackKeyFile).trim()), "AES");
        }

        private static void restrict(Path path) {
            try { Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE)); } catch (Exception ignored) {}
        }
        private static void putNullable(ObjectNode root,String name,String value){ if(value==null)root.putNull(name);else root.put(name,value); }
        private static String nullable(JsonNode root,String name){JsonNode n=root.get(name);return n==null||n.isNull()?null:n.asText(null);}
    }
}