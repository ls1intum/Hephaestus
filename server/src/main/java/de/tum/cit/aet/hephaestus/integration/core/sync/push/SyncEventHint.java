package de.tum.cit.aet.hephaestus.integration.core.sync.push;

/**
 * Wire payload for one {@code sync} SSE event — an invalidation hint, not data. Clients refetch the
 * relevant REST resource (design doc §3.5) rather than trusting a carried DTO, so this record stays
 * intentionally thin and vendor/DTO-shape agnostic.
 *
 * @param scope one of {@link Scope#wireValue()} — {@code job}, {@code resources}, {@code connection},
 *              {@code activity} ({@code activity} is reserved for a future publisher; {@link
 *              SyncPushService} only ever emits the first three today)
 * @param connectionId the connection this hint concerns
 * @param kind the {@code IntegrationKind} name (e.g. {@code "GITHUB"}) as a plain string so this
 *             record never depends on the SPI enum directly
 */
public record SyncEventHint(String scope, Long connectionId, String kind) {
    /** Mirrors {@code SyncStateChangedEvent.Scope} plus the reserved {@code ACTIVITY} value. */
    public enum Scope {
        JOB,
        RESOURCES,
        CONNECTION,
        ACTIVITY;

        public String wireValue() {
            return switch (this) {
                case JOB -> "job";
                case RESOURCES -> "resources";
                case CONNECTION -> "connection";
                case ACTIVITY -> "activity";
            };
        }
    }
}
