package com.snovasys.ledgerline.ratelimit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tiny JDK-HttpClient helper shared by the rate-limit HTTP tests (no extra test libraries needed). */
final class HttpTestSupport {

    record Resp(int status, String body, java.net.http.HttpHeaders headers) {
        String field(String name) {
            Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*(?:\"([^\"]*)\"|(-?[0-9.]+|true|false|null))").matcher(body);
            if (!m.find()) {
                return null;
            }
            return m.group(1) != null ? m.group(1) : m.group(2);
        }

        String header(String name) {
            return headers.firstValue(name).orElse(null);
        }
    }

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private HttpTestSupport() {
    }

    static Resp send(int port, String method, String path, Map<String, String> headers, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (json != null) {
            b.header("Content-Type", "application/json");
        }
        headers.forEach(b::header);
        b.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body(), r.headers());
    }

    static Resp post(int port, String path, String json) throws Exception {
        return send(port, "POST", path, Map.of(), json);
    }

    static String loginJson(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"WrongPass123\"}";
    }
}
