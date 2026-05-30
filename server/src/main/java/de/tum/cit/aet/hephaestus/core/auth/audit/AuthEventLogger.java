package de.tum.cit.aet.hephaestus.core.auth.audit;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Fluent front door for writing {@link AuthEvent} rows. Delegates the actual persistence
 * to {@link AuthEventWriter} (a separate bean, so the {@code REQUIRES_NEW} transaction
 * boundary is not bypassed by self-invocation).
 *
 * <p>Usage:
 * <pre>{@code
 * authEventLogger.event(LOGIN, SUCCESS).account(accountId).gitProvider(providerId).record();
 * }</pre>
 */
@Component
public class AuthEventLogger {

    private final AuthEventWriter writer;

    public AuthEventLogger(AuthEventWriter writer) {
        this.writer = writer;
    }

    public Draft event(AuthEvent.EventType type, AuthEvent.Result result) {
        return new Draft(type, result);
    }

    /** Fluent, null-tolerant builder. Terminal {@link #record()} persists via the writer. */
    public final class Draft {

        private final AuthEvent.EventType type;
        private final AuthEvent.Result result;
        private Long accountId;
        private Long actingAccountId;
        private String failureReason;
        private Long gitProviderId;
        private Long workspaceId;
        private Long identityLinkId;
        private String details;

        private Draft(AuthEvent.EventType type, AuthEvent.Result result) {
            this.type = type;
            this.result = result;
        }

        public Draft account(@Nullable Long id) {
            this.accountId = id;
            return this;
        }

        public Draft actingAccount(@Nullable Long id) {
            this.actingAccountId = id;
            return this;
        }

        public Draft failureReason(@Nullable String reason) {
            this.failureReason = reason;
            return this;
        }

        public Draft gitProvider(@Nullable Long id) {
            this.gitProviderId = id;
            return this;
        }

        public Draft workspace(@Nullable Long id) {
            this.workspaceId = id;
            return this;
        }

        public Draft identityLink(@Nullable Long id) {
            this.identityLinkId = id;
            return this;
        }

        public Draft details(@Nullable String json) {
            this.details = json;
            return this;
        }

        public void record() {
            writer.write(
                new AuthEventData(
                    type,
                    result,
                    accountId,
                    actingAccountId,
                    failureReason,
                    gitProviderId,
                    workspaceId,
                    identityLinkId,
                    details
                )
            );
        }
    }
}
