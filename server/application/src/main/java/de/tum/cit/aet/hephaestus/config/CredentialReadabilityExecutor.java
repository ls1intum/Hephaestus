package de.tum.cit.aet.hephaestus.config;

/**
 * Name of the executor every record of a stored credential's readability is written from.
 *
 * <p>A constant rather than a literal at the injection point, for the same reason as
 * {@link FeedbackLaneExecutor}: a typo in a qualifier is not an error, it silently resolves to the
 * default executor and undoes the isolation the pool exists to provide.
 */
public final class CredentialReadabilityExecutor {

    public static final String BEAN_NAME = "credentialReadabilityExecutor";

    private CredentialReadabilityExecutor() {}
}
