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
    List<AuthEvent> authEvents,
    List<DataDisclosure> dataDisclosures
) {
    /**
     * Current export schema version. Bump on any breaking shape change.
     *
     * <p>1.1 added {@code dataDisclosures}. Additive, so a 1.0 reader still parses a 1.1 document — the bump
     * is how such a reader learns there is now something it is not showing the subject.
     */
    public static final String SCHEMA_VERSION = "1.1";

    public record Profile(
        Long id,
        String displayName,
        @Nullable String primaryEmail,
        // appRole intentionally omitted: it is controller-assigned authorization state, NOT data the
        // subject "provided" under GDPR Art. 20(1) — out of portability scope. (Admin views surface it
        // elsewhere.) `status` is kept: it is the subject's own account-lifecycle state.
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

    /**
     * One occasion on which someone was shown this subject's practice data.
     *
     * <p>The only place this bundle names another person, and deliberately so: {@code authEvents} drops
     * impersonation rows under Art. 20(4) because portability covers data the subject provided, whereas this
     * section answers Art. 15(1)(c), which the CJEU held in C-154/21 entitles a subject to the recipients'
     * <em>identity</em> rather than their category.
     *
     * @param workspaceSlug  null once the subject has left that workspace — the disclosure outlives the
     *                       membership that names it
     * @param resourceType   {@code PRACTICE_REPORT} for this subject's own report; {@code PRACTICE_ROSTER}
     *                       for a list of the workspace's recently active developers, which named the subject
     *                       if they were active in that window
     * @param recipientLogin null once that person has been erased
     */
    public record DataDisclosure(
        Instant occurredAt,
        @Nullable String workspaceSlug,
        String resourceType,
        @Nullable String recipientLogin
    ) {}
}
