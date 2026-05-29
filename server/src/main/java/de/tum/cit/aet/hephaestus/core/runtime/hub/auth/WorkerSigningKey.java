package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One RSA signing key in the {@link WorkerKeyRing}. Identified by {@code kid} (the JWT
 * {@code kid} header claim) so a {@link WorkerJwtVerifier} can locate the correct verifier
 * during rotation.
 *
 * <p>The {@code ephemeral} flag is set when the key was generated at boot rather than loaded
 * from configuration — useful for dev/monolith mode where worker reconnects re-exchange the
 * JWT on app-pod restart and a fresh keypair has no continuity cost.
 *
 * @param kid       JWT {@code kid} header — non-empty, opaque to the protocol
 * @param publicKey verification side
 * @param privateKey signing side
 * @param ephemeral {@code true} when the key was generated at boot rather than loaded from config;
 *                  the wiring layer warns about this only under the prod profile
 */
public record WorkerSigningKey(String kid, RSAPublicKey publicKey, RSAPrivateKey privateKey, boolean ephemeral) {
    private static final Logger log = LoggerFactory.getLogger(WorkerSigningKey.class);

    public WorkerSigningKey {
        if (kid == null || kid.isBlank()) {
            throw new IllegalArgumentException("kid must not be blank");
        }
        if (publicKey == null) {
            throw new IllegalArgumentException("publicKey must not be null");
        }
        if (privateKey == null) {
            throw new IllegalArgumentException("privateKey must not be null");
        }
    }

    /** Parse a PKCS#8 PEM-encoded RSA private key, deriving the public key by modulus + exponent 65537. */
    public static WorkerSigningKey fromPem(String kid, String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("PEM must not be blank for kid=" + kid);
        }
        try {
            String stripped = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(stripped);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(der));
            // PKCS#8 PEM produces an {@link RSAPrivateCrtKey} for normal RSA private keys; that
            // carries the real public exponent. Reading {@code (modulus, 65537)} would silently
            // use the wrong public key for any keypair generated with a non-standard exponent.
            if (!(privateKey instanceof RSAPrivateCrtKey rsa)) {
                throw new IllegalArgumentException(
                    "expected RSA CRT private key (PKCS#8 with public exponent), got: " +
                        privateKey.getClass().getSimpleName()
                );
            }
            PublicKey publicKey = kf.generatePublic(new RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent()));
            return new WorkerSigningKey(kid, (RSAPublicKey) publicKey, rsa, false);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to parse signing key (kid=" + kid + ") as PKCS#8 PEM", e);
        }
    }

    /**
     * Generate an in-process keypair. Suitable for dev/monolith mode; production should
     * configure a stable PEM via {@code hephaestus.worker.hub.token.keys[*].private-key}.
     */
    public static WorkerSigningKey generateEphemeral(String kid) {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            // Caller (HubConfiguration) decides WARN-vs-INFO by profile; the record can't see it.
            log.debug("Generated ephemeral worker signing key (kid={})", kid);
            return new WorkerSigningKey(kid, (RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate(), true);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key generation unavailable", e);
        }
    }
}
