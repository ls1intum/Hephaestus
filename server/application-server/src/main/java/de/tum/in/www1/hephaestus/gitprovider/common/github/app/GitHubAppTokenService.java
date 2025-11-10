package de.tum.in.www1.hephaestus.gitprovider.common.github.app;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
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
    private final boolean credentialsConfigured;

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
        this.credentialsConfigured = isKeyMaterialPresent(appId, privateKeyRes, privateKeyPem);
        this.privateKey = credentialsConfigured ? loadKey(privateKeyRes, privateKeyPem) : generateEphemeralRsaKey();
    }

    /**
     * Build a short-lived App JWT.
     * - iat is backdated by 60s to tolerate small clock skew between systems.
     * - exp is set to 9 minutes to stay within the 10-minute GitHub limit with buffer.
     */
    public String generateAppJWT() {
        if (!isConfigured()) {
            throw new IllegalStateException("GitHub App credentials not configured.");
        }
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

            return new CachedToken(tok.getToken(), tok.getExpiresAt());
        } catch (IOException e) {
            throw new UncheckedIOException("GitHub error minting installation token for " + installationId, e);
        }
    }

    private static PrivateKey loadKey(Resource privateKeyRes, String privateKeyPem) {
        try {
            if (privateKeyPem != null && !privateKeyPem.isBlank()) {
                return parsePemPrivateKey(privateKeyPem);
            }
            if (privateKeyRes != null && privateKeyRes.exists()) {
                String pemKey = new String(privateKeyRes.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                return parsePemPrivateKey(pemKey);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read private key", e);
        }

        throw new IllegalStateException("No GitHub App private key configured.");
    }

    private static PrivateKey parsePemPrivateKey(String pem) {
        try {
            if (pem.contains("BEGIN RSA PRIVATE KEY")) {
                byte[] pkcs1 = decodePemBlock(pem, "RSA PRIVATE KEY");
                byte[] pkcs8 = convertPkcs1ToPkcs8(pkcs1);
                return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
            }

            byte[] der = decodePemBlock(pem, "PRIVATE KEY");
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid RSA private key", e);
        }
    }

    private static byte[] decodePemBlock(String pem, String type) {
        String normalized = pem.replace("\r", "");
        String beginMarker = "-----BEGIN " + type + "-----";
        String endMarker = "-----END " + type + "-----";
        int begin = normalized.indexOf(beginMarker);
        int end = normalized.indexOf(endMarker);
        if (begin < 0 || end < 0) {
            throw new IllegalArgumentException("PEM block for type '" + type + "' not found");
        }
        String base64 = normalized.substring(begin + beginMarker.length(), end).replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private static byte[] convertPkcs1ToPkcs8(byte[] pkcs1) {
        try {
            byte[] version = new byte[] { 0x02, 0x01, 0x00 };
            byte[] algorithmIdentifier = new byte[] {
                0x30,
                0x0d,
                0x06,
                0x09,
                0x2a,
                (byte) 0x86,
                0x48,
                (byte) 0x86,
                (byte) 0xf7,
                0x0d,
                0x01,
                0x01,
                0x01,
                0x05,
                0x00,
            };
            byte[] privateKeyOctetString = encodeDerOctetString(pkcs1);

            byte[] innerSequence = concat(version, algorithmIdentifier, privateKeyOctetString);
            return encodeDerSequence(innerSequence);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to convert PKCS#1 key to PKCS#8", e);
        }
    }

    private static byte[] encodeDerSequence(byte[] content) throws IOException {
        return encodeDerStructure((byte) 0x30, content);
    }

    private static byte[] encodeDerOctetString(byte[] content) throws IOException {
        return encodeDerStructure((byte) 0x04, content);
    }

    private static byte[] encodeDerStructure(byte tag, byte[] content) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            baos.write(tag);
            baos.write(encodeDerLength(content.length));
            baos.write(content);
            return baos.toByteArray();
        }
    }

    private static byte[] encodeDerLength(int length) {
        if (length < 0x80) {
            return new byte[] { (byte) length };
        }

        int numBytes = 0;
        int temp = length;
        byte[] buffer = new byte[4];
        while (temp > 0) {
            buffer[buffer.length - 1 - numBytes] = (byte) (temp & 0xFF);
            temp >>= 8;
            numBytes++;
        }

        byte[] result = new byte[1 + numBytes];
        result[0] = (byte) (0x80 | numBytes);
        System.arraycopy(buffer, buffer.length - numBytes, result, 1, numBytes);
        return result;
    }

    private static byte[] concat(byte[]... arrays) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (byte[] array : arrays) {
                baos.write(array);
            }
            return baos.toByteArray();
        }
    }

    private static boolean isKeyMaterialPresent(long appId, Resource privateKeyRes, String privateKeyPem) {
        if (appId <= 0) {
            return false;
        }
        if (privateKeyPem != null && !privateKeyPem.isBlank()) {
            return true;
        }
        if (privateKeyRes == null) {
            return false;
        }
        try {
            return privateKeyRes.exists() && privateKeyRes.contentLength() > 0;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static PrivateKey generateEphemeralRsaKey() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair().getPrivate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate ephemeral RSA private key", e);
        }
    }

    public boolean isConfigured() {
        return credentialsConfigured;
    }

    public long getConfiguredAppId() {
        return appId;
    }

    private record CachedToken(String token, Instant expiresAt) {}
}
