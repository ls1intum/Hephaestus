package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.Optional;

/** The authenticated account, or the validated account selected for a developer-facing read. */
public interface CurrentDeveloperLookup {
    Optional<Long> currentDeveloperId();

    long currentDeveloperIdElseThrow();
}
