package de.tum.cit.aet.hephaestus.integration.identity;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.Nullable;

/**
 * SPI for cross-integration user identity lookups.
 *
 * <p>Consumers: practice-detection author attribution, mentor activity aggregation,
 * leaderboard person resolution. Webhook-time identity resolution upserts
 * unlinked rows; OAuth-callback / Keycloak-federation linking attaches them to
 * {@link HephaestusUser}.
 */
public interface UserDirectory {
    /**
     * Look up the Hephaestus User behind a vendor-side identity.
     * Returns empty if the identity exists but is unlinked, or if it isn't yet observed.
     */
    Optional<HephaestusUser> findUser(IntegrationKind kind, long integrationInstanceId, String externalId);

    /** Idempotent upsert of an observed identity from a webhook actor block or backfill. */
    IntegrationIdentity upsertFromVendor(
        IntegrationKind kind,
        long integrationInstanceId,
        String externalId,
        VendorUserInfo info
    );

    /** Link an existing identity row to a Hephaestus User (typically after OAuth claim). */
    IntegrationIdentity linkToUser(IntegrationIdentity identity, HephaestusUser user);

    /** All vendor identities currently linked to the given Hephaestus User. */
    List<IntegrationIdentity> linkedIdentitiesFor(HephaestusUser user);

    /** Identities matching the email — used by the verified-email auto-link path. */
    List<IntegrationIdentity> findByEmail(String email);

    record VendorUserInfo(
        @Nullable String externalLogin,
        @Nullable String externalEmail,
        @Nullable String displayName,
        @Nullable String rawAttributesJson
    ) {}
}
