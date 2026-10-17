package de.tum.cit.aet.hephaestus.integration.core.spi;

import org.jspecify.annotations.Nullable;

public record SummaryLookup(
        LookupPresence presence, @Nullable SummaryHandle handle) {
    public SummaryLookup {

        if ((presence == LookupPresence.FOUND) != (handle != null))
            throw new IllegalArgumentException("Only a found summary has a handle");
    }

    public static SummaryLookup found(SummaryHandle handle) {
        return new SummaryLookup(LookupPresence.FOUND, handle);
    }

    public static SummaryLookup absent() {
        return new SummaryLookup(LookupPresence.ABSENT, null);
    }

    public static SummaryLookup unknown() {
        return new SummaryLookup(LookupPresence.UNKNOWN, null);
    }
}
