package de.tum.cit.aet.hephaestus.core.auth.export;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The structured GDPR Art. 20 export document. Serialized to JSON as the {@link AccountExport}
 * payload. Top-level {@code generatedAt} + {@code schemaVersion} bracket the contents so the
 * format can evolve.
 *
 * <p>This type is the explicit contract for what we disclose. It deliberately has NO field for
 * tokens, credential blobs, signing keys, password-equivalents, or other users' data — the
 * absence is the control. Adding such a field here is the only place it could leak, so this
 * record is the review chokepoint.
 */
public record ExportBundle(
    String schemaVersion,
    Instant generatedAt,
    Profile account,
    List<Identity> identities,
    List<WorkspaceMembership> workspaceMemberships,
    List<String> featureFlags,
    @Nullable Preferences preferences,
    List<AuthEvent> authEvents
) {
    /** Current export schema version. Bump on any breaking shape change. */
    public static final String SCHEMA_VERSION = "1.0";

    public record Profile(
        Long id,
        String displayName,
        @Nullable String primaryEmail,
        String appRole,
        String status,
        Instant createdAt
    ) {}

    public record Identity(
        String provider,
        String subject,
        @Nullable String usernameAtSignup,
        @Nullable String emailAtSignup,
        @Nullable String displayName,
        Instant linkedAt,
        @Nullable Instant lastLoginAt
    ) {}

    public record WorkspaceMembership(@Nullable String slug, @Nullable String name, @Nullable String role) {}

    public record Preferences(boolean participateInResearch, boolean aiReviewEnabled) {}

    public record AuthEvent(
        Instant occurredAt,
        String eventType,
        String result,
        @Nullable String ip,
        @Nullable String userAgent
    ) {}
}
