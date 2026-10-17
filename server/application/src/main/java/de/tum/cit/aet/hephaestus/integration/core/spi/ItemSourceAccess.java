package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import java.util.Set;

/** Checks current source permission without resolving an item version or capturing evidence. */
public interface ItemSourceAccess {
    Set<ItemKind> kinds();

    boolean permits(ItemRef item, SourceUsePurpose purpose);
}
