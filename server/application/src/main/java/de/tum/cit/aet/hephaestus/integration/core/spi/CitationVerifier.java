package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;
import java.util.Set;

/** Kind-specific citation and claim-basis checks. Core performs the generic checks itself.
 * This port keeps provider implementations outside the review pipeline, which cannot import adapter types. */
public interface CitationVerifier {
    Set<ItemKind> kinds();

    /** Checks captured side, commit and change-basis references. Empty means verified. */
    List<CitationRefusal> verify(CitationCheck check);
}
