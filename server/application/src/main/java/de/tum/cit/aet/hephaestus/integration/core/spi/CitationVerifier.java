package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;
import java.util.Set;

/** Kind-specific citation and claim-basis checks. Core performs the generic checks itself. */
public interface CitationVerifier {
    /** Item kinds this verifier handles. */
    Set<ItemKind> kinds();

    /** Checks captured side, revision and change-basis references. Empty means verified. */
    List<CitationRefusal> verify(CitationCheck check);
}
