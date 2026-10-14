package de.tum.cit.aet.hephaestus.practices.spi;

import org.jspecify.annotations.Nullable;

public record BoundModelSlot(
        String slot, ModelKind kind, @Nullable FrozenModel model) {}
