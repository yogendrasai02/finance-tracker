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
 * Tests built on this assert on the raw Set-Cookie attributes and on which token is echoed in which header.
 * A client that manages cookies for us would hide exactly those details.
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
        return send("GET", path, null);
    }

    public HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        return send("POST", path, body);
    }

    /**
     * Sends any HTTP method for tests that sweep the routing table instead of driving one known flow.
     *
     * A null body means no body is sent and no content type is set.
     * The CSRF token is attached for state-changing methods only because CsrfFilter runs before the authorization filter.
     * Without the token a state-changing request is rejected as 403, which would hide the 401 a security sweep is trying to observe.
     *
     * @param method the HTTP method
     * @param path the request path
     * @param body the request body, or null when the request has no body
     * @return the HTTP response
     * @throws IOException if the request cannot be sent
     * @throws InterruptedException if the request is interrupted
     */
    public HttpResponse<String> send(String method, String path, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = request(path);
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        String token = cookies.get(csrfCookieName);
        if (token != null && changesState(method)) {
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

    /**
     * Returns the raw header for a cookie set by the given response.
     * This allows callers to check the cookie attributes.
     */
    public String setCookieHeader(HttpResponse<String> response, String name) {
        return response.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith(name + "="))
                .reduce((first, second) -> second)
                .orElse(null);
    }

    /** True for the methods CSRF protection applies to, which is every method except the safe ones. */
    private static boolean changesState(String method) {
        return !("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method) || "TRACE".equals(method));
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

    /**
     * Stores a response cookie or removes it when the response deletes the cookie.
     * A cookie with an empty value or a zero max age is treated as a deletion.
     */
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
