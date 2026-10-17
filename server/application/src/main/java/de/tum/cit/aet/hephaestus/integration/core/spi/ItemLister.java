package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.Set;

public interface ItemLister {
    ItemKind containerKind();

    Set<ItemKind> memberKinds();

    boolean supports(ItemRef container);

    ItemPage list(ItemListRequest request);
}
