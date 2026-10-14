package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;
import java.util.Set;

public interface ItemDescriptor {
    ItemKind kind();

    String displayName();

    List<ItemEventDefinition> events();

    Set<AboutRole> roles();

    Set<CopyCapability> copyCapabilities();

    boolean reviewable();

    Set<ItemKind> memberKinds();

    String recordSchemaResource();

    ReviewCapabilities reviewCapabilities();
}
