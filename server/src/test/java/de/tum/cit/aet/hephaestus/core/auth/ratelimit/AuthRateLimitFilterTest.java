package de.tum.cit.aet.hephaestus.core.auth.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for the rate-limit filter's endpoint matching, key derivation, and 429 contract.
 *
 * <p>Uses an in-JVM {@link BucketResolver} (a {@code ConcurrentHashMap} of local buckets) so the
 * key/limit logic is exercised without a database — the same seam the production code wires to a
 * Postgres ProxyManager.
 */
class AuthRateLimitFilterTest extends BaseUnitTest {

    private final Map<String, Bucket> store = new ConcurrentHashMap<>();
    private final Map<String, BucketConfiguration> capturedConfigs = new ConcurrentHashMap<>();

    private final BucketResolver resolver = (key, config) -> {
        capturedConfigs.put(key, config);
        return store.computeIfAbsent(key, k -> Bucket.builder().addLimit(config.getBandwidths()[0]).build());
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuthRateLimitFilter filter(AuthRateLimitProperties props) {
        return new AuthRateLimitFilter(props, resolver, objectMapper);
    }

    private static AuthRateLimitProperties props(AuthRateLimitProperties.Limit... overrides) {
        return propsWithTrustedProxies(1, overrides);
    }

    private static AuthRateLimitProperties propsWithTrustedProxies(
        int trustedProxyCount,
        AuthRateLimitProperties.Limit... overrides
    ) {
        // Defaults from the spec; tests override specific limits via the canonical constructor.
        return new AuthRateLimitProperties(
            true,
            trustedProxyCount,
            overrides.length > 0 ? overrides[0] : new AuthRateLimitProperties.Limit(20, Duration.ofMinutes(1)),
            new AuthRateLimitProperties.Limit(60, Duration.ofMinutes(1)),
            new AuthRateLimitProperties.Limit(10, Duration.ofMinutes(1)),
            new AuthRateLimitProperties.Limit(3, Duration.ofHours(1))
        );
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(String subject) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "ES256")
            .subject(subject)
            .claim("roles", java.util.List.of("admin"))
            .build();
        AbstractAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void nonMatchingPathPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/workspaces");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter(props()).doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertThat(store).isEmpty();
    }

    @Test
    void disabledFilterPassesThroughWithoutTouchingBuckets() throws Exception {
        AuthRateLimitProperties disabled = new AuthRateLimitProperties(
            false,
            1,
            new AuthRateLimitProperties.Limit(1, Duration.ofMinutes(1)),
            new AuthRateLimitProperties.Limit(1, Duration.ofMinutes(1)),
            new AuthRateLimitProperties.Limit(1, Duration.ofMinutes(1)),
            new AuthRateLimitProperties.Limit(1, Duration.ofHours(1))
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter(disabled).doFilter(request, response, chain);
        filter(disabled).doFilter(request, response, chain);

        verify(chain, times(2)).doFilter(request, response);
        assertThat(store).isEmpty();
    }

    @Test
    void oauthAuthorizationIsKeyedByClientIpAndCapped() throws Exception {
        // capacity 2 to keep the test fast
        AuthRateLimitProperties p = props(new AuthRateLimitProperties.Limit(2, Duration.ofMinutes(1)));
        AuthRateLimitFilter f = filter(p);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
            req.addHeader("X-Forwarded-For", "203.0.113.7");
            MockHttpServletResponse res = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            f.doFilter(req, res, chain);
            verify(chain, times(1)).doFilter(req, res);
            assertThat(res.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        // third request from the same IP is rejected
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        req.addHeader("X-Forwarded-For", "203.0.113.7");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        f.doFilter(req, res, chain);

        verify(chain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(res.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(res.getHeader(HttpHeaders.RETRY_AFTER)).isNotNull();
        assertThat(Long.parseLong(res.getHeader(HttpHeaders.RETRY_AFTER))).isGreaterThanOrEqualTo(1);
        assertThat(res.getContentAsString()).contains("Too Many Requests").contains("retryAfterSeconds");
        assertThat(store).containsOnlyKeys("oauth-authz:ip:203.0.113.7");
    }

    @Test
    void differentIpsGetIndependentOauthBuckets() throws Exception {
        AuthRateLimitProperties p = props(new AuthRateLimitProperties.Limit(1, Duration.ofMinutes(1)));
        AuthRateLimitFilter f = filter(p);

        MockHttpServletRequest a = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        a.addHeader("X-Forwarded-For", "198.51.100.1");
        MockHttpServletResponse ra = new MockHttpServletResponse();
        FilterChain ca = mock(FilterChain.class);
        f.doFilter(a, ra, ca);

        MockHttpServletRequest b = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        b.addHeader("X-Forwarded-For", "198.51.100.2");
        MockHttpServletResponse rb = new MockHttpServletResponse();
        FilterChain cb = mock(FilterChain.class);
        f.doFilter(b, rb, cb);

        verify(ca, times(1)).doFilter(a, ra);
        verify(cb, times(1)).doFilter(b, rb);
        assertThat(store).containsOnlyKeys("oauth-authz:ip:198.51.100.1", "oauth-authz:ip:198.51.100.2");
    }

    @Test
    void refreshIsKeyedByAccountSubjectWhenAuthenticated() throws Exception {
        authenticateAs("4242");
        AuthRateLimitFilter f = filter(props());

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/refresh");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        f.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(store).containsOnlyKeys("refresh:acct:4242");
        // verify the configured limit (60/min) reached the resolver for this endpoint
        assertThat(capturedConfigs.get("refresh:acct:4242").getBandwidths()[0].getCapacity()).isEqualTo(60);
    }

    @Test
    void refreshFallsBackToIpWhenUnauthenticated() throws Exception {
        AuthRateLimitFilter f = filter(props());

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/refresh");
        req.setRemoteAddr("192.0.2.55");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        f.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(store).containsOnlyKeys("refresh:ip:192.0.2.55");
    }

    @Test
    void impersonateBeginIsRateLimitedButExitIsNot() throws Exception {
        authenticateAs("99");
        AuthRateLimitProperties p = props();
        AuthRateLimitFilter f = filter(p);

        MockHttpServletRequest begin = new MockHttpServletRequest("POST", "/auth/impersonate");
        f.doFilter(begin, new MockHttpServletResponse(), mock(FilterChain.class));
        assertThat(store).containsOnlyKeys("impersonate:acct:99");

        // exit verb must NOT be matched (separate path, equals-not-startsWith guard)
        MockHttpServletRequest exit = new MockHttpServletRequest("POST", "/auth/impersonate:exit");
        MockHttpServletResponse exitRes = new MockHttpServletResponse();
        FilterChain exitChain = mock(FilterChain.class);
        f.doFilter(exit, exitRes, exitChain);

        verify(exitChain, times(1)).doFilter(exit, exitRes);
        assertThat(store).containsOnlyKeys("impersonate:acct:99"); // no new bucket for exit
    }

    @Test
    void deleteUserUsesThreePerHourBudgetKeyedByAccount() throws Exception {
        authenticateAs("7");
        // use the spec default (3/hour) — exhaust then assert 429
        AuthRateLimitFilter f = filter(props());

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            f.doFilter(new MockHttpServletRequest("DELETE", "/user"), res, chain);
            assertThat(res.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        FilterChain blockedChain = mock(FilterChain.class);
        f.doFilter(new MockHttpServletRequest("DELETE", "/user"), blocked, blockedChain);

        verify(blockedChain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(store).containsOnlyKeys("delete-user:acct:7");
        assertThat(capturedConfigs.get("delete-user:acct:7").getBandwidths()[0].getCapacity()).isEqualTo(3);
    }

    @Test
    void workerAndWebhookPathsAreNeverRateLimited() throws Exception {
        AuthRateLimitFilter f = filter(props());

        for (String path : new String[] { "/api/workers/exchange", "/webhooks/gitlab", "/actuator/health" }) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
            MockHttpServletResponse res = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            f.doFilter(req, res, chain);
            verify(chain, times(1)).doFilter(req, res);
        }
        assertThat(store).isEmpty();
    }

    @Test
    void singleTrustedProxyTakesRightmostXffHopAsClient() throws Exception {
        // One Coolify hop: the proxy appends the real client IP, so XFF has exactly one entry which
        // IS the client. getRemoteAddr() is the proxy and must be ignored.
        AuthRateLimitFilter f = filter(props());

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        req.addHeader("X-Forwarded-For", "203.0.113.7");
        req.setRemoteAddr("10.0.0.1");
        f.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(store).containsOnlyKeys("oauth-authz:ip:203.0.113.7");
    }

    @Test
    void spoofedLeftmostXffEntriesAreIgnoredWithSingleTrustedProxy() throws Exception {
        // Attacker prepends bogus hops to forge a fresh bucket: "evil1, evil2, <real-client>".
        // With one trusted proxy the only trustworthy entry is the rightmost one (the address our
        // proxy appended). The leftmost spoofed values MUST NOT key the bucket.
        AuthRateLimitFilter f = filter(props());

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        req.addHeader("X-Forwarded-For", "1.1.1.1, 2.2.2.2, 203.0.113.50");
        req.setRemoteAddr("10.0.0.1");
        f.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(store).containsOnlyKeys("oauth-authz:ip:203.0.113.50");
    }

    @Test
    void spoofedXffCannotMintUnlimitedBucketsAcrossRequests() throws Exception {
        // capacity 1: an attacker rotating the leftmost spoofed value must still collapse into the
        // SAME bucket (the rightmost trusted hop) and get 429 on the second attempt.
        AuthRateLimitFilter f = filter(props(new AuthRateLimitProperties.Limit(1, Duration.ofMinutes(1))));

        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        first.addHeader("X-Forwarded-For", "9.9.9.1, 203.0.113.50");
        first.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse firstRes = new MockHttpServletResponse();
        FilterChain firstChain = mock(FilterChain.class);
        f.doFilter(first, firstRes, firstChain);
        verify(firstChain, times(1)).doFilter(first, firstRes);

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        second.addHeader("X-Forwarded-For", "9.9.9.2, 203.0.113.50"); // rotated spoof, same real client
        second.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse secondRes = new MockHttpServletResponse();
        FilterChain secondChain = mock(FilterChain.class);
        f.doFilter(second, secondRes, secondChain);

        verify(secondChain, never()).doFilter(second, secondRes);
        assertThat(secondRes.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(store).containsOnlyKeys("oauth-authz:ip:203.0.113.50");
    }

    @Test
    void tooFewHopsForTrustedProxiesFallsBackToRemoteAddr() throws Exception {
        // Two trusted proxies configured but only one XFF hop present → the chain is shorter than
        // claimed, so the header is untrustworthy and we fall back to the unforgeable remote address.
        AuthRateLimitFilter f = filter(propsWithTrustedProxies(2));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        req.addHeader("X-Forwarded-For", "203.0.113.99");
        req.setRemoteAddr("198.51.100.200");
        f.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(store).containsOnlyKeys("oauth-authz:ip:198.51.100.200");
    }

    @Test
    void zeroTrustedProxiesAlwaysUsesRemoteAddr() throws Exception {
        // trustedProxyCount=0 disables XFF trust entirely — only getRemoteAddr() keys the bucket.
        AuthRateLimitFilter f = filter(propsWithTrustedProxies(0));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        req.addHeader("X-Forwarded-For", "203.0.113.7");
        req.setRemoteAddr("198.51.100.5");
        f.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(store).containsOnlyKeys("oauth-authz:ip:198.51.100.5");
    }
}
