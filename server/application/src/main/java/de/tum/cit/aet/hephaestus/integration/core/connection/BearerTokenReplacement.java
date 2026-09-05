package de.tum.cit.aet.hephaestus.integration.core.connection;

/**
 * The outcome of replacing a connection's bearer token: the saved connection and whether a credential
 * was there to replace, both read inside the write so a caller audits what happened and not what an
 * earlier look at the row suggested.
 */
public record BearerTokenReplacement(Connection connection, boolean replacedExisting) {}
