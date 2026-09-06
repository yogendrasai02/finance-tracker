package com.financetracker.common.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * The filter chain, written deny-by-default: an endpoint added later is protected without depending on the coder to protect it (SR-35).
 *
 * Two public paths, both listed by method as well as path, and everything else authenticated.
 * Static assets are not listed because this application serves none: the frontend is served separately, and a rule for files that do not exist is untested surface.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfiguration {

    static final String LOGIN_PATH = "/api/v1/auth/login";

    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    private static final String HEALTH_PATH = "/actuator/health";

    /**
     * The backend answers with JSON and never renders a page, so nothing needs to be loaded from anywhere.
     * This tightens further once the built frontend is served from here.
     */
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    private static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository,
            SecurityErrorHandler securityErrorHandler,
            TenantContextFilter tenantContextFilter,
            AbsoluteSessionTimeoutFilter absoluteSessionTimeoutFilter,
            LoginRateLimitFilter loginRateLimitFilter,
            @Value("${server.servlet.session.cookie.name}") String sessionCookieName)
            throws Exception {

        return http.authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, LOGIN_PATH)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, HEALTH_PATH)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                // spa() pairs the cookie repository with a request handler that reads the plain token out of a header, which is what a browser application can send back.
                // It also loads the token eagerly, so every response carries the cookie, including the 401 the frontend gets before anyone has logged in.
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .logout(logout -> logout.logoutRequestMatcher(
                                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, LOGOUT_PATH))
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies(sessionCookieName))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                // Without this, every rejected request stores itself in a new session so the user can be sent back to it after logging in.
                // That is a server-rendered app's flow. Here it only means an unauthenticated caller can create sessions, and each one becomes a row once the session store moves to Postgres.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .headers(headers -> headers.contentSecurityPolicy(policy -> policy.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(
                                hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)))
                // Neither is used, and both would otherwise offer a second way in.
                .httpBasic(basic -> basic.disable())
                .formLogin(login -> login.disable())
                // Before the context is loaded, so an absolute-expired session is destroyed rather than handed over as a valid Authentication (SR-38).
                .addFilterBefore(absoluteSessionTimeoutFilter, SecurityContextHolderFilter.class)
                // Ahead of every other security filter, so an over-limit login is rejected before a session or a CSRF token is even considered.
                // Registered after the line above: a custom filter class only gets a position in the chain once something has anchored it, and this filter's own anchor is that one.
                .addFilterBefore(loginRateLimitFilter, AbsoluteSessionTimeoutFilter.class)
                // Last in the chain, so the security context is loaded and authorization has already passed.
                .addFilterAfter(tenantContextFilter, AuthorizationFilter.class)
                .build();
    }

    /**
     * Hides the real reason a login failed, and compares against a dummy hash when no user matches, so a wrong email and a wrong password take about the same time (SR-39).
     * Both behaviours are the provider's defaults; they are the reason to use it rather than checking the hash by hand.
     */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * The CSRF token is the one cookie the frontend has to read, so it is deliberately not {@code HttpOnly} (D-39).
     * That is safe because another origin cannot read this site's cookies, which is the whole basis of the double-submit pattern.
     */
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Strict").path("/"));
        return repository;
    }

    /** The same pair Spring Security wires by default, defined here so the login controller saves into the store the chain reads from. */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    /**
     * What a login filter would apply automatically, in the order it applies it.
     * The session id changes first, then the CSRF token is reissued against the new session.
     */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        CsrfAuthenticationStrategy csrfStrategy = new CsrfAuthenticationStrategy(csrfTokenRepository);
        csrfStrategy.setRequestHandler(eagerCsrfTokenRequestHandler());
        return new CompositeSessionAuthenticationStrategy(
                List.of(new ChangeSessionIdAuthenticationStrategy(), csrfStrategy));
    }

    /**
     * A CSRF handler that resolves the token instead of deferring it, which is what makes the new cookie reach the response.
     *
     * The strategy above always deletes the old token, but only writes the replacement if something asks for its value during the request.
     * With the default handler nothing does, so a login would clear the browser's token and hand back no new one, and the next state-changing request would be refused.
     * Clearing the attribute name is the documented way to opt out of deferred loading: the handler then reads the token's parameter name, and reading anything off the token resolves it.
     */
    private static CsrfTokenRequestAttributeHandler eagerCsrfTokenRequestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }
}
