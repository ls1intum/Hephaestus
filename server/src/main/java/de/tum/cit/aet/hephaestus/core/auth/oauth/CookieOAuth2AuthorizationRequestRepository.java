package de.tum.cit.aet.hephaestus.core.auth.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Stores the in-flight {@link OAuth2AuthorizationRequest} in an AES-GCM-encrypted cookie
 * rather than the HTTP session. Required because every existing Hephaestus filter chain
 * is {@code STATELESS} — the Spring default
 * {@code HttpSessionOAuth2AuthorizationRequestRepository} would silently create a session
 * that the callback request cannot see on the next pod (the failure mode Wave-2 PE flagged
 * as the most likely "doesn't work on multi-pod" bug).
 *
 * <h2>Cookie</h2>
 * Name {@value #COOKIE_NAME}; {@code HttpOnly}, {@code Secure}, {@code SameSite=Lax}
 * ({@code Strict} would break the cross-site IdP callback), {@code Path=/}, 10-minute TTL.
 *
 * <h2>Encryption</h2>
 * AES-256-GCM. Nonce is the first 12 bytes; ciphertext+tag follow. AAD is the literal
 * {@code "oauth2-state"} — distinct from any tenant or system AAD so a confused-deputy
 * substitution between domains is rejected.
 *
 * <h2>Multi-flight</h2>
 * v1 supports a single in-flight authorization request per browser. Opening a second
 * login tab while the first is mid-flight will invalidate the first. Acceptable for
 * launch UX; LRU multi-flight cookie can be added if real users hit issues.
 */
public class CookieOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String COOKIE_NAME = "__Host-OAUTH_STATE";
    private static final String AAD = "oauth2-state";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MAX_COOKIE_AGE_SECONDS = 600; // 10 minutes
    private static final Logger log = LoggerFactory.getLogger(CookieOAuth2AuthorizationRequestRepository.class);
    private static final SecureRandom RNG = new SecureRandom();

    private final SecretKeySpec key;

    public CookieOAuth2AuthorizationRequestRepository(byte[] aesKeyBytes) {
        if (aesKeyBytes.length != 32) {
            throw new IllegalArgumentException("AES-GCM cookie key must be 32 bytes (256-bit), got " + aesKeyBytes.length);
        }
        this.key = new SecretKeySpec(aesKeyBytes, "AES");
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Cookie cookie = findCookie(request);
        if (cookie == null) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cookie.getValue());
            byte[] plain = decrypt(decoded);
            return deserialize(plain);
        } catch (RuntimeException ex) {
            log.warn("auth.oauth: rejecting tampered/expired oauth-state cookie: {}", ex.getMessage());
            return null;
        }
    }

    @Override
    public void saveAuthorizationRequest(
        OAuth2AuthorizationRequest authorizationRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        if (authorizationRequest == null) {
            clear(response);
            return;
        }
        byte[] plain = serialize(authorizationRequest);
        byte[] sealed = encrypt(plain);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(sealed);
        Cookie cookie = new Cookie(COOKIE_NAME, encoded);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(MAX_COOKIE_AGE_SECONDS);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        OAuth2AuthorizationRequest loaded = loadAuthorizationRequest(request);
        if (loaded != null) {
            clear(response);
        }
        return loaded;
    }

    private void clear(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    private Cookie findCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    private byte[] encrypt(byte[] plain) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RNG.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(AAD.getBytes());
            byte[] ct = cipher.doFinal(plain);
            byte[] out = new byte[nonce.length + ct.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(ct, 0, out, nonce.length, ct.length);
            return out;
        } catch (Exception ex) {
            throw new IllegalStateException("oauth-state encrypt failed", ex);
        }
    }

    private byte[] decrypt(byte[] sealed) {
        if (sealed.length < NONCE_BYTES + (TAG_BITS / 8)) {
            throw new IllegalStateException("oauth-state cookie too short");
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] ct = new byte[sealed.length - NONCE_BYTES];
            System.arraycopy(sealed, 0, nonce, 0, NONCE_BYTES);
            System.arraycopy(sealed, NONCE_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(AAD.getBytes());
            return cipher.doFinal(ct);
        } catch (Exception ex) {
            throw new IllegalStateException("oauth-state decrypt failed", ex);
        }
    }

    private static byte[] serialize(OAuth2AuthorizationRequest req) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(req);
            oos.flush();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("oauth-state serialize failed", ex);
        }
    }

    private static OAuth2AuthorizationRequest deserialize(byte[] bytes) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object obj = ois.readObject();
            if (!(obj instanceof OAuth2AuthorizationRequest req)) {
                throw new IllegalStateException("oauth-state cookie did not deserialize to OAuth2AuthorizationRequest");
            }
            return req;
        } catch (Exception ex) {
            throw new IllegalStateException("oauth-state deserialize failed", ex);
        }
    }
}
