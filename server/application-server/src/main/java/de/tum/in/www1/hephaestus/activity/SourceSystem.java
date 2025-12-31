package de.tum.in.www1.hephaestus.activity;

/**
 * Source system that generated an activity event.
 *
 * <p>Identifies the origin of domain events to support multi-provider scenarios
 * and distinguish between externally-triggered vs system-generated events.
 *
 * <p><strong>Note:</strong> Currently the EventContext doesn't carry provider information,
 * so GITHUB is assumed for all webhook/sync events. When GitLab support is added,
 * EventContext should be extended with a provider field.
 */
public enum SourceSystem {
    /**
     * Event originated from GitHub (webhook or API sync).
     */
    GITHUB("github"),

    /**
     * Event originated from GitLab (webhook or API sync).
     * <p>TODO: Activate when GitLab webhook support is added.
     */
    GITLAB("gitlab"),

    /**
     * Event generated internally by the system (e.g., scheduled tasks, backfill).
     */
    SYSTEM("system");

    private final String value;

    SourceSystem(String value) {
        this.value = value;
    }

    /**
     * Get the string value stored in the database.
     */
    public String getValue() {
        return value;
    }

    /**
     * Convert from database string value to enum.
     *
     * @param value the database value
     * @return the matching enum
     * @throws IllegalArgumentException if value is null or unknown
     */
    public static SourceSystem fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Source system value cannot be null");
        }
        for (SourceSystem source : values()) {
            if (source.value.equals(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown source system: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
