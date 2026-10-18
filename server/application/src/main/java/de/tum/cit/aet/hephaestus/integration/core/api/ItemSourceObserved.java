package de.tum.cit.aet.hephaestus.integration.core.api;

/** A source write can establish current facts even when its review occasion already exists. */
public record ItemSourceObserved(long workspaceId, long itemId) {}
