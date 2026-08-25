package de.tum.cit.aet.hephaestus.core.security;

import java.util.Optional;

/**
 * Request-scoped override for "who is the current SCM user", set by {@code WorkspaceContextFilter} while
 * a request is scoped to a workspace.
 *
 * <p>A single Hephaestus account (ADR 0017) can link several provider identities, but the cookie-JWT
 * carries only one {@code preferred_username} (the login the session signed in with). Inside a workspace
 * the relevant identity is the account's SCM user for THAT workspace's provider (a GitHub workspace →
 * the GitHub identity), which is generally not the session's login. The filter resolves that identity
 * and stashes its login here so the whole request — {@link SecurityUtils#getCurrentUserLogin()} and
 * everything downstream ({@code UserRepository.getCurrentUser()}, profile/activity/membership/mentor) —
 * resolves the provider-correct user. Cleared in the filter's {@code finally} (like the workspace
 * context). Absent outside a workspace request, where {@code preferred_username} remains authoritative.
 */
public final class CurrentScmIdentityHolder {

    private static final ThreadLocal<String> LOGIN = new ThreadLocal<>();

    private CurrentScmIdentityHolder() {}

    /** Override the current SCM login for the remainder of this request. */
    public static void set(String login) {
        LOGIN.set(login);
    }

    /** The workspace-scoped SCM login, if one was set for this request. */
    public static Optional<String> get() {
        return Optional.ofNullable(LOGIN.get());
    }

    /** MUST be called in a {@code finally} so the ThreadLocal never leaks across pooled threads. */
    public static void clear() {
        LOGIN.remove();
    }
}
