/**
 * Account module — user identity, preferences, and linked-account management.
 *
 * <p>Owns {@code user_preferences} and the API surface for account self-service.
 * Linked-account state is sourced from the {@code core.auth} identity links, exposed via the
 * identity-provider controller.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Account")
package de.tum.cit.aet.hephaestus.account;
