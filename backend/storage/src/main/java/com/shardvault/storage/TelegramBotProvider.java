package com.shardvault.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Experimental Telegram Bot API storage. Intended for encrypted replica chunks only. */
public final class TelegramBotProvider implements StorageProvider {
    public static final long MAX_CHUNK_BYTES = 15L * 1024 * 1024;
    private static final long MIN_SEND_INTERVAL_NANOS = Duration.ofSeconds(3).toNanos();
    private final String id;
    private final String token;
    private final String chatId;
    private final ProviderLimits limits;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final java.util.concurrent.ConcurrentMap<String, String> logicalKeys = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> deletedKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private long nextSendNanos;

    public TelegramBotProvider(String id, String token, String chatId, ProviderLimits limits) {
        this(id, token, chatId, limits, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL).build(), new ObjectMapper());
    }

    TelegramBotProvider(String id, String token, String chatId, ProviderLimits limits, HttpClient client, ObjectMapper mapper) {
        this.id = id;
        this.token = token;
        this.chatId = chatId;
        long configured = limits == null ? MAX_CHUNK_BYTES : limits.maxChunkBytes().orElse(MAX_CHUNK_BYTES);
        this.limits = new ProviderLimits(Optional.of(Math.min(MAX_CHUNK_BYTES, configured)),
                limits == null ? Optional.empty() : limits.maxObjectBytes(),
                Optional.of("20 messages/minute/chat; paced at 1 message per 3 seconds"));
        this.client = client;
        this.mapper = mapper;
    }

    @Override public String id() { return id; }
    @Override public ProviderType type() { return ProviderType.TELEGRAM_BOT; }
    @Override public ReliabilityTier reliability() { return ReliabilityTier.LOW; }

    @Override
    public void put(String objectKey, InputStream data, long length) {
        putAndGetKey(objectKey, data, length);
    }

    @Override
    public String putAndGetKey(String objectKey, InputStream data, long length) {
        if (length > limits.maxChunkBytes().orElse(MAX_CHUNK_BYTES)) {
            throw new ProviderConfigurationException("Telegram chunk exceeds provider maxChunkBytes");
        }
        try {
            byte[] content = readBounded(data, limits.maxChunkBytes().orElse(MAX_CHUNK_BYTES));
            paceSends();
            String boundary = "----ShardVault" + java.util.UUID.randomUUID().toString().replace("-", "");
            ByteArrayOutputStream body = new ByteArrayOutputStream(content.length + 1024);
            write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + chatId + "\r\n");
            write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"caption\"\r\n\r\n" + objectKey + "\r\n");
            write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"document\"; filename=\"chunk.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n");
            body.write(content);
            write(body, "\r\n--" + boundary + "--\r\n");
            JsonNode result = api("sendDocument", HttpRequest.newBuilder(apiUri("sendDocument"))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build());
            String fileId = result.path("result").path("document").path("file_id").asText(null);
            long messageId = result.path("result").path("message_id").asLong(-1);
            if (fileId == null || messageId < 0) throw new ProviderUnavailableException("Telegram sendDocument response was incomplete");
            // object key is the Telegram file_id plus message_id needed for deletion.
            // The caller's logical key is retained in the caption for operational diagnosis.
            String remoteKey = fileId + "." + messageId;
            logicalKeys.put(objectKey, remoteKey);
            deletedKeys.remove(objectKey);
            deletedKeys.remove(remoteKey);
            return remoteKey;
        } catch (IOException ex) {
            throw new ProviderUnavailableException("Telegram upload failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException("Telegram upload interrupted", ex);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        if (deletedKeys.contains(objectKey) || deletedKeys.contains(resolveKey(objectKey))) throw new ObjectNotFoundException(objectKey);
        try {
            String fileId = fileId(resolveKey(objectKey));
            JsonNode file = api("getFile", jsonRequest("getFile", mapper.createObjectNode().put("file_id", fileId)));
            String path = file.path("result").path("file_path").asText(null);
            if (path == null) throw new ObjectNotFoundException(objectKey);
            HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(URI.create("https://api.telegram.org/file/bot" + token + "/" + path))
                    .timeout(Duration.ofMinutes(2)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) throw new ObjectNotFoundException(objectKey);
            if (response.statusCode() / 100 != 2) throw new ProviderUnavailableException("Telegram file download returned HTTP " + response.statusCode());
            return new java.io.ByteArrayInputStream(response.body());
        } catch (IOException ex) {
            throw new ProviderUnavailableException("Telegram download failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException("Telegram download interrupted", ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            long messageId = messageId(resolveKey(objectKey));
            api("deleteMessage", jsonRequest("deleteMessage", mapper.createObjectNode().put("chat_id", chatId).put("message_id", messageId)));
            deletedKeys.add(objectKey);
            deletedKeys.add(resolveKey(objectKey));
            logicalKeys.remove(objectKey);
        } catch (IOException ex) {
            throw new ProviderUnavailableException("Telegram delete failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException("Telegram delete interrupted", ex);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        if (deletedKeys.contains(objectKey) || deletedKeys.contains(resolveKey(objectKey))) return false;
        try {
            api("getFile", jsonRequest("getFile", mapper.createObjectNode().put("file_id", fileId(resolveKey(objectKey)))));
            return true;
        } catch (ProviderException ex) {
            if (ex instanceof ObjectNotFoundException) return false;
            throw ex;
        } catch (IOException ex) {
            throw new ProviderUnavailableException("Telegram exists check failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException("Telegram exists check interrupted", ex);
        }
    }

    @Override
    public HealthStatus health() {
        Instant start = Instant.now();
        try {
            api("getMe", HttpRequest.newBuilder(apiUri("getMe")).timeout(Duration.ofSeconds(20)).GET().build());
            return HealthStatus.up(Duration.between(start, Instant.now()).toMillis());
        } catch (Exception ex) {
            return HealthStatus.down(Duration.between(start, Instant.now()).toMillis());
        }
    }

    @Override public Optional<Long> freeBytes() { return Optional.empty(); }
    @Override public ProviderLimits limits() { return limits; }

    private synchronized void paceSends() {
        long now = System.nanoTime();
        long wait = nextSendNanos - now;
        if (wait > 0) {
            try { Thread.sleep(Math.max(1, Duration.ofNanos(wait).toMillis())); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new ProviderUnavailableException("Telegram send interrupted", ex); }
        }
        nextSendNanos = System.nanoTime() + MIN_SEND_INTERVAL_NANOS;
    }

    private JsonNode api(String method, HttpRequest request) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = mapper.readTree(response.body());
            if (response.statusCode() == 429) {
                long retry = body.path("parameters").path("retry_after").asLong(1);
                if (attempt == 2) throw new ProviderUnavailableException("Telegram rate limit persisted after retry");
                Thread.sleep(Math.max(1, retry) * 1000);
                continue;
            }
            if (response.statusCode() == 400 && (method.equals("getFile")
                    || body.path("description").asText().toLowerCase().contains("file_id"))) {
                throw new ObjectNotFoundException("telegram-object");
            }
            if (response.statusCode() / 100 != 2 || !body.path("ok").asBoolean(false)) {
                throw new ProviderUnavailableException("Telegram API " + method + " failed (HTTP " + response.statusCode() + ")");
            }
            return body;
        }
        throw new ProviderUnavailableException("Telegram API retry exhausted");
    }

    private HttpRequest jsonRequest(String method, com.fasterxml.jackson.databind.JsonNode body) {
        return HttpRequest.newBuilder(apiUri(method)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
    }

    private URI apiUri(String method) { return URI.create("https://api.telegram.org/bot" + token + "/" + method); }
    private String resolveKey(String key) { return logicalKeys.getOrDefault(key, key); }
    private static String fileId(String key) { int dot = key.lastIndexOf('.'); if (dot <= 0) throw new ObjectNotFoundException(key); return key.substring(0, dot); }
    private static long messageId(String key) { int dot = key.lastIndexOf('.'); if (dot <= 0) throw new ObjectNotFoundException(key); try { return Long.parseLong(key.substring(dot + 1)); } catch (NumberFormatException ex) { throw new ObjectNotFoundException(key, ex); } }
    private static byte[] readBounded(InputStream input, long max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; long count = 0; int read;
        while ((read = input.read(buffer)) != -1) { count += read; if (count > max) throw new ProviderConfigurationException("Telegram chunk exceeds provider maxChunkBytes"); out.write(buffer, 0, read); }
        return out.toByteArray();
    }
    private static void write(ByteArrayOutputStream out, String value) throws IOException { out.write(value.getBytes(StandardCharsets.UTF_8)); }
}
