package de.tum.cit.aet.hephaestus.gitprovider.webhook.gitlab;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Constant-time comparison of {@code X-Gitlab-Token} against the shared secret. GitLab does not
 * sign payloads; the token IS the secret. See {@link HmacVerifier} for the constant-time +
 * length-tolerant {@link MessageDigest#isEqual} contract.
 */
public final class GitLabTokenVerifier {

    private GitLabTokenVerifier() {}

    public static boolean verify(String token, String secret) {
        if (token == null || token.isEmpty() || secret == null || secret.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8));
    }
}
