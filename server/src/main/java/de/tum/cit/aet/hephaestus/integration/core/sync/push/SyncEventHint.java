package de.tum.cit.aet.hephaestus.integration.core.sync.push;

/**
 * Wire payload for one {@code sync} SSE event — an invalidation hint, not data. Clients refetch the
 * relevant REST resource (design doc §3.5) rather than trusting a carried DTO, so this record stays
 * intentionally thin and vendor/DTO-shape agnostic.
 *
 * @param scope one of {@link Scope#wireValue()} — {@code job}, {@code resources}, {@code connection},
 *              {@code activity}. All four are emitted today: {@code ConnectionActivityRecorder}
 *              publishes {@code activity} on every processed webhook event, the others on job and
 *              resource state changes.
 * @param connectionId the connection this hint concerns — the only field the client keys its
 *                      query-invalidation off (alongside {@code scope}); no vendor/kind is carried
 *                      because the hint is an invalidation trigger, not data.
 */
public record SyncEventHint(String scope, Long connectionId) {
    /** Mirrors {@code SyncStateChangedEvent.Scope}; all four values are published today. */
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
