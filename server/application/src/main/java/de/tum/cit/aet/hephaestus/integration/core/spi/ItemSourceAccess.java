package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import java.util.Set;

/** Checks current source permission without resolving an item version or capturing evidence. */
public interface ItemSourceAccess {
    Set<ItemKind> kinds();

    /** The check for {@link SourceReadAudience#PROCESSING}: every read by or for AI. */
    boolean permits(ItemRef item, SourceUsePurpose purpose);

    /**
     * The check for one audience. An adapter that has not decided what a human recorded standing may read refuses it,
     * so a new source never widens that read by omission.
     */
    default boolean permits(ItemRef item, SourceUsePurpose purpose, SourceReadAudience audience) {
        return audience == SourceReadAudience.PROCESSING && permits(item, purpose);
    }
}
