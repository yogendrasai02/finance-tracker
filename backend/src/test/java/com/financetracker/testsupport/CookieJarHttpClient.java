package com.financetracker.testsupport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A cookie jar and nothing else, driving the running application over real HTTP.
 *
 * Written by hand rather than handed to an HTTP client library on purpose.
 * Tests built on this assert on the raw {@code Set-Cookie} attributes and on which token is echoed in which header, and a client that manages cookies for us would hide exactly those details.
 */
public final class CookieJarHttpClient {

    private final HttpClient client = HttpClient.newHttpClient();

    private final Map<String, String> cookies = new LinkedHashMap<>();

    private final String baseUrl;

    private final String csrfCookieName;

    private final String csrfHeaderName;

    public CookieJarHttpClient(int port, String csrfCookieName, String csrfHeaderName) {
        this.baseUrl = "http://localhost:" + port;
        this.csrfCookieName = csrfCookieName;
        this.csrfHeaderName = csrfHeaderName;
    }

    public HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send(request(path).GET());
    }

    public HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        HttpRequest.Builder request = postRequest(path, body);
        String token = cookies.get(csrfCookieName);
        if (token != null) {
            request.header(csrfHeaderName, token);
        }
        return send(request);
    }

    public HttpResponse<String> postWithoutCsrfToken(String path, String body) throws IOException, InterruptedException {
        return send(postRequest(path, body));
    }

    public String cookie(String name) {
        return cookies.get(name);
    }

    /** The raw header for a cookie the given response set, so the attributes on it can be checked. */
    public String setCookieHeader(HttpResponse<String> response, String name) {
        return response.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith(name + "="))
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private HttpRequest.Builder postRequest(String path, String body) {
        return request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookies.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .reduce((first, second) -> first + "; " + second)
                    .orElseThrow());
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        response.headers().allValues("set-cookie").forEach(this::store);
        return response;
    }

    /** A cookie with an empty value or a zero max age is a deletion, so it leaves the jar rather than entering it. */
    private void store(String setCookie) {
        String pair = setCookie.split(";", 2)[0];
        int separator = pair.indexOf('=');
        if (separator < 0) {
            return;
        }
        String name = pair.substring(0, separator).trim();
        String value = pair.substring(separator + 1).trim();
        if (value.isEmpty() || setCookie.contains("Max-Age=0")) {
            cookies.remove(name);
        } else {
            cookies.put(name, value);
        }
    }
}
