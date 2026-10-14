package de.tum.cit.aet.hephaestus.mentor;

import de.tum.cit.aet.hephaestus.core.security.SecurityUtils;
import java.util.Optional;

public final class MentorTurnAccount {
    private static final ThreadLocal<Long> ACCOUNT = new ThreadLocal<>();

    private MentorTurnAccount() {}

    public static Optional<Long> currentId() {
        return Optional.ofNullable(ACCOUNT.get())
                .or(SecurityUtils::getCurrentAccountId)
                .filter(id -> id > 0);
    }

    public static long requireId() {
        return currentId()
                .orElseThrow(() -> new IllegalStateException("A mentor turn requires an authenticated account"));
    }

    public static void set(long accountId) {
        if (accountId <= 0) throw new IllegalArgumentException("Account id must be positive");
        ACCOUNT.set(accountId);
    }

    public static void clear() {
        ACCOUNT.remove();
    }
}
