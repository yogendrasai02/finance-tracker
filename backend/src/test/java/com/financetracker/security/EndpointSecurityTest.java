package com.financetracker.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.financetracker.account.AccountResponse;
import com.financetracker.account.AccountService;
import com.financetracker.common.error.NotFoundException;
import com.financetracker.testsupport.CookieJarHttpClient;
import com.financetracker.testsupport.TwoUserTestHarness;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

/**
 * SR-07 IDOR and authentication coverage test.
 *
 * Two guarantees are driven over real HTTP.
 *
 * Authentication coverage: every route not listed in PUBLIC_ROUTES must reject an anonymous caller with 401.
 * The route list is read from the live routing table, so a controller added without a security rule fails here.
 *
 * Tenant isolation (IDOR): calling as user B with user A's id returns 404, not 403 and not A's data.
 *
 * Adding coverage for a new id-taking endpoint means adding one test method.
 */
class EndpointSecurityTest extends TwoUserTestHarness {

    /**
     * The routes that are intentionally reachable without a session.
     *
     * Method and path together, because the filter chain permits by method and path together.
     * A path-only list would silently exempt GET /api/v1/auth/login, which is authenticated.
     * Every other discovered route must return 401 anonymously, so opening one up means editing this set, and that edit is the review signal.
     */
    private static final Set<Route> PUBLIC_ROUTES = Set.of(
            new Route("POST", "/api/v1/auth/login"),
            new Route("GET", "/actuator/health"));

    /** Registered by the nested test configuration below, so the routing table has an id-taking route to exercise. */
    private static final String PROBE_PATH = "/api/v1/test-probe/accounts";

    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    /**
     * Every request-mapping-based handler mapping in the context, injected by type rather than by bean name.
     *
     * There are four: the application's controllers, the actuator's web endpoints, the actuator's controller endpoints, and the additional health paths.
     * Asking for the requestMappingHandlerMapping bean by name would sweep the first and silently ignore the other three.
     */
    @Autowired
    private List<RequestMappingInfoHandlerMapping> handlerMappings;

    // ---- authentication coverage ----

    /**
     * Sweeps every discovered route and asserts an anonymous caller gets 401.
     *
     * Soft assertions, so one run reports every unprotected route rather than only the first.
     * This is a single test rather than a parameterised one on purpose: a @MethodSource must be static and cannot see the injected handler mappings.
     * That limitation would push the test toward a hand-written route list.
     */
    @Test
    void everyNonPublicRouteRejectsAnonymousCallers() {
        Set<Route> routes = discoverRoutes();
        SoftAssertions softly = new SoftAssertions();

        for (Route route : routes) {
            if (PUBLIC_ROUTES.contains(route)) {
                continue;
            }
            int status = statusForAnonymous(route);
            softly.assertThat(status)
                    .as("%s %s must reject a caller with no session", route.method(), route.path())
                    .isEqualTo(401);
        }

        softly.assertAll();
    }

    /**
     * Guards the sweep above against passing for the wrong reason.
     *
     * An empty or shrunken route set would make it pass while testing nothing.
     */
    @Test
    void discoveryFindsTheKnownRoutes() {
        Set<Route> routes = discoverRoutes();

        assertThat(routes)
                .as("the routing table must contain the routes built so far")
                .contains(
                        new Route("POST", "/api/v1/auth/login"),
                        new Route("GET", "/api/v1/me"),
                        new Route("GET", "/api/v1/accounts"),
                        new Route("GET", "/actuator/health"));
    }

    /**
     * Proves the public set exempts real routes.
     *
     * Without this, a typo in PUBLIC_ROUTES would exempt nothing, and the sweep would still pass while login and health were broken.
     */
    @Test
    void publicRoutesAreReachableWithoutASession() throws Exception {
        CookieJarHttpClient browser = anonymousBrowser();
        browser.get("/api/v1/me");

        HttpResponse<String> health = browser.get("/actuator/health");
        HttpResponse<String> login = browser.post("/api/v1/auth/login", credentials(userAEmail, PASSWORD));

        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(login.statusCode()).isEqualTo(200);
    }

    /**
     * Logout is checked by hand because discovery cannot see it.
     *
     * It is Spring Security's own logout filter, not a controller method, so it appears in no routing table (4e).
     * The sweep would therefore never notice if it broke.
     */
    @Test
    void logoutIsNotDiscoverableAndSoIsCheckedByHand() throws Exception {
        assertThat(discoverRoutes())
                .as("if logout ever becomes a controller method, delete this test and let the sweep cover it")
                .noneMatch(route -> route.path().equals(LOGOUT_PATH));

        CookieJarHttpClient browser = freshLoginAs(userBEmail);
        HttpResponse<String> logout = browser.post(LOGOUT_PATH, "");

        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(browser.get("/api/v1/me").statusCode())
                .as("the session must be gone after logout")
                .isEqualTo(401);
    }

    /**
     * Every rejection shares one body (SR-04, SR-78).
     *
     * A body that differed by route would say which paths exist.
     */
    @Test
    void unauthenticatedRejectionsShareOneBody() throws Exception {
        CookieJarHttpClient browser = anonymousBrowser();

        String onAKnownRoute = browser.get("/api/v1/accounts").body();
        String onAnUnknownRoute = browser.get("/api/v1/accounts/does-not-exist").body();

        assertThat(onAKnownRoute).isEqualTo(onAnUnknownRoute);
        assertThat(onAKnownRoute).contains("Authentication is required");
        assertThat(onAKnownRoute).doesNotContain(userAEmail).doesNotContain(userBEmail);
    }

    // ---- tenant isolation ----

    /**
     * The IDOR case the harness exists for: user B asks for user A's account by id.
     *
     * The answer must be 404, the same body a missing id gets, because "not yours" and "does not exist" are one response (SR-04, SR-78).
     * A 403 would confirm the row exists and belongs to someone.
     */
    @Test
    void userBAskingForUserAsAccountByIdGets404() throws Exception {
        CookieJarHttpClient browserB = loggedInAs(userBEmail);

        HttpResponse<String> crossTenant = browserB.get(PROBE_PATH + "/" + userAAccountId);
        HttpResponse<String> missing = browserB.get(PROBE_PATH + "/" + Long.MAX_VALUE);

        assertThat(crossTenant.statusCode()).isEqualTo(404);
        assertThat(withoutInstance(crossTenant.body()))
                .as("another tenant's row and a row that never existed must be indistinguishable")
                .isEqualTo(withoutInstance(missing.body()));
        assertThat(crossTenant.body()).doesNotContain("A Savings");
    }

    /** The same endpoint returns the caller's own row, so the 404 above is isolation rather than a broken endpoint. */
    @Test
    void userBAskingForTheirOwnAccountByIdGetsIt() throws Exception {
        CookieJarHttpClient browserB = loggedInAs(userBEmail);

        HttpResponse<String> response = browserB.get(PROBE_PATH + "/" + userBAccountId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("B Savings");
    }

    /** Tenant isolation on the list endpoint: user B's response contains none of user A's data. */
    @Test
    void userBCannotSeeUserAAccounts() throws Exception {
        CookieJarHttpClient browserB = loggedInAs(userBEmail);

        HttpResponse<String> response = browserB.get("/api/v1/accounts");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("B Savings");
        assertThat(response.body()).doesNotContain("A Savings");
    }

    /** The identity on /me comes from the session, so there is no id to tamper with. */
    @Test
    void meReturnsOnlyTheCallersOwnProfile() throws Exception {
        CookieJarHttpClient browserA = loggedInAs(userAEmail);
        CookieJarHttpClient browserB = loggedInAs(userBEmail);

        HttpResponse<String> profileA = browserA.get("/api/v1/me");
        HttpResponse<String> profileB = browserB.get("/api/v1/me");

        assertThat(profileA.body()).contains(userAEmail).doesNotContain(userBEmail);
        assertThat(profileB.body()).contains(userBEmail).doesNotContain(userAEmail);
    }

    // ---- discovery ----

    /** Reads every route from every request-mapping handler mapping in the context. */
    private Set<Route> discoverRoutes() {
        Set<Route> routes = new LinkedHashSet<>();
        for (RequestMappingInfoHandlerMapping mapping : handlerMappings) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
                RequestMappingInfo info = entry.getKey();
                Set<String> methods = new TreeSet<>();
                info.getMethodsCondition().getMethods().forEach(method -> methods.add(method.name()));
                if (methods.isEmpty()) {
                    // No method condition means the route answers every method; GET is enough to prove the chain rejects it.
                    methods.add("GET");
                }
                for (String pattern : info.getPatternValues()) {
                    for (String method : methods) {
                        routes.add(new Route(method, pattern));
                    }
                }
            }
        }
        return routes;
    }

    /** Sends one anonymous request for a discovered route and returns the status. */
    private int statusForAnonymous(Route route) {
        try {
            CookieJarHttpClient browser = anonymousBrowser();
            if (!HttpMethod.GET.name().equals(route.method())) {
                // Primes the CSRF cookie, so a state-changing request is refused for want of a session rather than a token.
                browser.get("/api/v1/me");
            }
            String body = HttpMethod.GET.name().equals(route.method()) ? null : "{}";
            return browser.send(route.method(), concreteUrl(route.path()), body).statusCode();
        } catch (Exception e) {
            throw new IllegalStateException("could not probe " + route.method() + " " + route.path(), e);
        }
    }

    /**
     * Turns a route pattern into a URL that can actually be sent.
     *
     * A pattern is sent literally otherwise, and /api/v1/accounts/{id} is not a valid URL.
     * The substituted value never reaches a handler: the security filter chain runs first, which is the whole subject of this test.
     */
    private static String concreteUrl(String pattern) {
        return pattern
                .replaceAll("\\{[^/}]+}", "1")
                .replace("/**", "/probe")
                .replace("*", "probe");
    }

    /**
     * Removes the RFC 7807 instance field, which is the requested URI.
     *
     * Two 404s for different ids differ in that field and nowhere else.
     * It echoes back the path the caller just asked for, so it tells them nothing they did not already type, and comparing the rest is what SR-04 and SR-78 actually require.
     */
    private static String withoutInstance(String body) {
        return body.replaceAll("\"instance\":\"[^\"]*\"", "\"instance\":\"...\"");
    }

    /** One HTTP method and one path pattern. */
    private record Route(String method, String path) {
    }

    // ---- the id-taking endpoint the IDOR cases need ----

    /**
     * Registers a by-id account endpoint that exists only in this test's context.
     *
     * The application has no id-taking endpoint yet, and an IDOR harness with nothing to point at proves nothing.
     * This route goes through the real chain — session, tenant context, Row-Level Security — so the 404 it returns for another tenant's id is produced by the mechanism under test, not by the test.
     * Delete it when GET /api/v1/accounts/{id} ships, and point these cases at the real route.
     */
    @TestConfiguration
    static class ProbeEndpointConfiguration {

        @Bean
        AccountProbeController accountProbeController(AccountService accountService) {
            return new AccountProbeController(accountService);
        }
    }

    @RestController
    static class AccountProbeController {

        private final AccountService accountService;

        AccountProbeController(AccountService accountService) {
            this.accountService = accountService;
        }

        /**
         * Reads one account by id from the caller's own accounts.
         *
         * Row-Level Security has already removed every other tenant's rows by the time this filters by id, so another tenant's id simply is not there.
         */
        @GetMapping(PROBE_PATH + "/{id}")
        AccountResponse byId(@PathVariable long id) {
            return accountService.listAccounts().stream()
                    .filter(account -> account.id() == id)
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("account " + id));
        }
    }
}
