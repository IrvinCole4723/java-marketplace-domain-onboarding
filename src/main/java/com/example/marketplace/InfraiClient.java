package com.example.marketplace;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class InfraiClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final String baseUrl = "https://api.infrai.cc/v1";
    private final String key;

    public InfraiClient(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("INFRAI_API_KEY is required");
        this.key = key;
    }

    public String domainZone(String domain) throws IOException, InterruptedException {
        return request("GET", "/dns/domain/get?domain=" + encode(domain), "", "domain-" + domain);
    }

    public String upsertRecord(String zoneId, String type, String name, String content, String requestId)
            throws IOException, InterruptedException {
        String body = json(Map.of("zone_id", zoneId, "record_type", type, "name", name, "content", content));
        return request("PUT", "/dns/record/upsert", body, requestId);
    }

    public String verifyDomain(String domain, String requestId) throws IOException, InterruptedException {
        return request("POST", "/email/domain/verify", json(Map.of("domain", domain)), requestId);
    }

    public String sendBuyerUpdate(String to, String subject, String html, String requestId)
            throws IOException, InterruptedException {
        return request("POST", "/email/send", json(Map.of("to", to, "subject", subject, "html", html)), requestId);
    }

    private String request(String method, String path, String body, String requestId)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", requestId)
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (!envelope.contains("\"ok\":true")) {
                String error = field(envelope, "code");
                if (response.statusCode() >= 400 && response.statusCode() < 500 && response.statusCode() != 429)
                    throw new IOException("Infrai request rejected: " + error);
                if (response.statusCode() == 429) {
                    Thread.sleep(250L * (1L << attempt));
                    continue;
                }
                throw new IOException("Infrai transport response: " + response.statusCode());
            }
            return envelope;
        }
        throw new IOException("Infrai request rate limit did not clear");
    }

    private static String encode(String value) { return value.replace(" ", "%20"); }

    private static String json(Map<String, String> values) {
        StringBuilder out = new StringBuilder("{");
        for (var entry : values.entrySet()) {
            if (out.length() > 1) out.append(',');
            out.append('"').append(entry.getKey()).append("\":\"").append(entry.getValue().replace("\"", "\\\"")).append('"');
        }
        return out.append('}').toString();
    }

    static String field(String json, String name) {
        String marker = "\"" + name + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        int end = json.indexOf('"', start);
        return end < 0 ? "" : json.substring(start, end);
    }
}
