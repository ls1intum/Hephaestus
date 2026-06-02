package de.tum.cit.aet.hephaestus.core.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Pins the AES-GCM-sealed auth-intent cookie's server-side freshness gate: the {@code issuedAt} baked
 * into the sealed plaintext (not the client-controlled {@code Max-Age}) is authoritative, so a stale
 * or future-dated cookie is treated as absent (returns null).
 */
class AuthIntentCookieTest extends BaseUnitTest {

    private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes();
    private static final Instant T0 = Instant.parse("2026-06-01T00:00:00Z");

    private static AuthIntentCookie at(Instant when) {
        return new AuthIntentCookie(KEY, Clock.fixed(when, ZoneOffset.UTC));
    }

    /** Seal {@code intent} (writer clock irrelevant) and return the cookie value. */
    private static Cookie seal(AuthIntentCookie.Intent intent) {
        MockHttpServletResponse res = new MockHttpServletResponse();
        new AuthIntentCookie(KEY).write(res, intent);
        return res.getCookie(AuthIntentCookie.COOKIE_NAME);
    }

    private static AuthIntentCookie.Intent readBack(AuthIntentCookie reader, Cookie cookie) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(cookie);
        return reader.read(req);
    }

    private static AuthIntentCookie.Intent intentAt(long issuedAtMillis) {
        return new AuthIntentCookie.Intent("ws", "/x", AuthIntentCookie.Intent.Mode.LOGIN, null, issuedAtMillis);
    }

    @Test
    void freshCookie_roundTrips() {
        Cookie cookie = seal(intentAt(T0.toEpochMilli()));
        AuthIntentCookie.Intent read = readBack(at(T0), cookie);
        assertThat(read).isNotNull();
        assertThat(read.workspaceSlug()).isEqualTo("ws");
        assertThat(read.returnTo()).isEqualTo("/x");
        assertThat(read.mode()).isEqualTo(AuthIntentCookie.Intent.Mode.LOGIN);
    }

    @Test
    void cookieJustWithinTtl_returnsIntent() {
        Cookie cookie = seal(intentAt(T0.toEpochMilli()));
        assertThat(readBack(at(T0.plusSeconds(599)), cookie)).isNotNull();
    }

    @Test
    void cookieOlderThanTtl_returnsNull() {
        Cookie cookie = seal(intentAt(T0.toEpochMilli()));
        assertThat(readBack(at(T0.plusSeconds(601)), cookie)).isNull();
    }

    @Test
    void futureDatedCookie_returnsNull() {
        Cookie cookie = seal(intentAt(T0.toEpochMilli()));
        assertThat(readBack(at(T0.minusSeconds(5)), cookie)).isNull();
    }

    @Test
    void legacyCookieWithoutIssuedAt_isTreatedAsStale() {
        // issuedAt == 0 mimics a cookie minted before the field existed → ~56 years old → rejected.
        Cookie cookie = seal(intentAt(0L));
        assertThat(readBack(at(T0), cookie)).isNull();
    }

    @Test
    void tamperedCookie_returnsNull() {
        Cookie cookie = seal(intentAt(T0.toEpochMilli()));
        String v = cookie.getValue();
        Cookie tampered = new Cookie(AuthIntentCookie.COOKIE_NAME, (v.charAt(0) == 'A' ? 'B' : 'A') + v.substring(1));
        assertThat(readBack(at(T0), tampered)).isNull();
    }
}
