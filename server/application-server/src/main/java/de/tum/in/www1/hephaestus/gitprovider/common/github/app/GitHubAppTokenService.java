package de.tum.in.www1.hephaestus.gitprovider.common.github.app;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class GitHubAppTokenService {

    private final long appId;
    private final PrivateKey privateKey;

    // Cache installation tokens to avoid re-minting on every call.
    private final Cache<Long, CachedToken> installTokenCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(55)) // safety cap; GitHub returns precise expiry which we track below
        .maximumSize(10000)
        .build();

    public GitHubAppTokenService(
        @Value("${github.app.id}") long appId,
        @Value("${github.app.privateKeyLocation:}") Resource privateKeyRes,
        @Value("${github.app.privateKey:}") String privateKeyPem
    ) {
        this.appId = appId;
        this.privateKey = loadKey(privateKeyRes, privateKeyPem);
    }

    /**
     * Build a short-lived App JWT.
     * - iat is backdated by 60s to tolerate small clock skew between systems.
     * - exp is set to 9 minutes to stay within the 10-minute GitHub limit with buffer.
     */
    public String generateAppJWT() {
        Instant now = Instant.now();
        Algorithm algorithm = Algorithm.RSA256(null, (RSAPrivateKey) privateKey);
        return JWT.create()
            .withIssuer(String.valueOf(appId))
            .withIssuedAt(java.util.Date.from(now.minusSeconds(60))) // tolerate skew
            .withExpiresAt(java.util.Date.from(now.plusSeconds(9 * 60))) // < 10 minutes hard limit
            .sign(algorithm);
    }

    /**
     * Return a valid installation access token for the given installation.
     * Uses the cached token if it exists and has >60s of validity left; otherwise refreshes.
     */
    public String getInstallationToken(long installationId) {
        CachedToken cached = installTokenCache.getIfPresent(installationId);
        if (cached != null && cached.expiresAt.isAfter(Instant.now().plusSeconds(60))) {
            return cached.token;
        }
        CachedToken fresh = installTokenCache.get(installationId, this::fetchTokenWithExpiry);
        if (fresh == null) {
            throw new IllegalStateException("Failed to mint installation token for " + installationId);
        }
        // If token returned with very little validity remaining, force a proactive refresh.
        if (fresh.expiresAt.isBefore(Instant.now().plusSeconds(30))) {
            fresh = fetchTokenWithExpiry(installationId);
            installTokenCache.put(installationId, fresh);
        }

        return fresh.token;
    }

    /** Hub4J client authenticated as the installation (use this for repo/PR calls). */
    public GitHub clientForInstallation(long installationId) throws IOException {
        String token = getInstallationToken(installationId);
        // Hub4J accepts installation tokens with OAuth token setter.
        return new GitHubBuilder().withOAuthToken(token).build();
    }

    /** Hub4J client authenticated as the App (via JWT). */
    public GitHub clientAsApp() throws IOException {
        return new GitHubBuilder().withJwtToken(generateAppJWT()).build();
    }

    /** Mint a fresh installation token as the App and capture its expiry. */
    private CachedToken fetchTokenWithExpiry(long installationId) {
        try {
            String appJwt = generateAppJWT();
            GitHub asApp = new GitHubBuilder().withJwtToken(appJwt).build();
            GHAppInstallation appInstallation = asApp.getApp().getInstallationById(installationId);
            GHAppInstallationToken tok = appInstallation.createToken().create();

            return new CachedToken(tok.getToken(), tok.getExpiresAt().toInstant());
        } catch (IOException e) {
            throw new UncheckedIOException("GitHub error minting installation token for " + installationId, e);
        }
    }

    private static PrivateKey loadKey(Resource privateKeyRes, String privateKeyPem) {
        String pemKey;
        try {
            if (privateKeyRes != null && privateKeyRes.exists()) {
                pemKey = new String(privateKeyRes.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } else if (privateKeyPem != null && !privateKeyPem.isBlank()) {
                pemKey = privateKeyPem;
            } else {
                throw new IllegalStateException("No GitHub App private key configured.");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read private key", e);
        }
        return loadPkcs8RsaPrivateKey(pemKey);
    }

    private static PrivateKey loadPkcs8RsaPrivateKey(String pem) {
        String clean = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(clean);
        try {
            var spec = new PKCS8EncodedKeySpec(der);

            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid PKCS#8 RSA private key", e);
        }
    }

    private record CachedToken(String token, Instant expiresAt) {}
}
