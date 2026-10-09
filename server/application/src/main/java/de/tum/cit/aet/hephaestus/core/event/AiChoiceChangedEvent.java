package de.tum.cit.aet.hephaestus.core.event;

/** A person chose whether AI may read their work. Account-wide: every workspace of the account is affected. */
public record AiChoiceChangedEvent(long accountId) {}
