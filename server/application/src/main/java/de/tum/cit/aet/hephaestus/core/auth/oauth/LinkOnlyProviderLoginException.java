package de.tum.cit.aet.hephaestus.core.auth.oauth;

/** Raised when a secondary identity provider is used as a primary sign-in method. */
public class LinkOnlyProviderLoginException extends RuntimeException {

    public LinkOnlyProviderLoginException(String registrationId) {
        super("provider requires authenticated account linking: " + registrationId);
    }
}
