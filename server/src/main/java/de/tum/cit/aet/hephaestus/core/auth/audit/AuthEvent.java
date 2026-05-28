package de.tum.cit.aet.hephaestus.core.auth.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.lang.Nullable;

/**
 * Append-only auth-event log. Monthly partitioned via {@code pg_partman} (12-month retention,
 * oldest auto-dropped). Records the {@code (account_id, acting_account_id)} pair for every
 * {@code SwitchUserFilter}-driven impersonation so every action attributable to an impersonator
 * is reconstructible.
 *
 * <h2>Tamper-evidence</h2>
 * Non-test environments revoke UPDATE / DELETE on this table at the SQL grant level — the
 * row is insert-only. Backups + WAL archive provide forensic recovery.
 *
 * <h2>IP truncation</h2>
 * {@code ip_inet} is stored verbatim for 30 days then truncated to /24 (IPv4) / /48 (IPv6) by
 * a scheduled job — meets GDPR Art. 30 records-of-processing without exposing precise IPs in
 * long-term storage.
 *
 * <p>Composite PK {@code (id, occurred_at)} because partitioned tables in Postgres require
 * the partition key in every unique index.
 */
@Entity
@Table(name = "auth_event")
@Getter
@NoArgsConstructor
public class AuthEvent {

    @EmbeddedId
    private Id id;

    /** Account that the event is about (the target of impersonation, the deleted user, …). */
    @Column(name = "account_id")
    @Nullable
    private Long accountId;

    /** Impersonator, if the event was performed under a {@code SwitchUserFilter}-bound session. */
    @Column(name = "acting_account_id")
    @Nullable
    private Long actingAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 48)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 16)
    private Result result;

    @Column(name = "failure_reason", length = 64)
    @Nullable
    private String failureReason;

    @Column(name = "git_provider_id")
    @Nullable
    private Long gitProviderId;

    @Column(name = "workspace_id")
    @Nullable
    private Long workspaceId;

    @Column(name = "identity_link_id")
    @Nullable
    private Long identityLinkId;

    @Column(name = "ip_inet", nullable = false, columnDefinition = "inet")
    private String ipInet;

    @Column(name = "user_agent", length = 512)
    @Nullable
    private String userAgent;

    /** SHA-256 of the JWT {@code jti} — never store the raw token / session id. */
    @Column(name = "session_hash")
    @Nullable
    private byte[] sessionHash;

    @Column(name = "request_id", columnDefinition = "uuid")
    @Nullable
    private UUID requestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    @Nullable
    private String details;

    public enum EventType {
        LOGIN,
        LOGIN_FAILED,
        LOGOUT,
        TOKEN_REFRESH,
        JWT_REVOKED,
        IDENTITY_LINKED,
        IDENTITY_UNLINKED,
        IMPERSONATION_BEGIN,
        IMPERSONATION_END,
        ACCOUNT_DELETED,
        ACCOUNT_DELETION_CANCELLED,
        EXPORT_REQUESTED,
        FEATURE_FLAG_CHANGED,
    }

    public enum Result {
        SUCCESS,
        FAILURE,
    }

    /**
     * Factory used by {@link AuthEventWriter} — the only sanctioned construction path.
     * Keeps the entity {@code @Getter}-only (append-only invariant). Business fields come
     * via the {@link AuthEventData} parameter object; request-derived metadata is supplied
     * directly. {@code requestId} / {@code sessionHash} are reserved columns, currently null.
     */
    public static AuthEvent create(
        AuthEventData data,
        Long id,
        Instant occurredAt,
        String ipInet,
        @Nullable String userAgent
    ) {
        AuthEvent e = new AuthEvent();
        e.id = new Id(id, occurredAt);
        e.eventType = data.type();
        e.result = data.result();
        e.accountId = data.accountId();
        e.actingAccountId = data.actingAccountId();
        e.failureReason = data.failureReason();
        e.gitProviderId = data.gitProviderId();
        e.workspaceId = data.workspaceId();
        e.identityLinkId = data.identityLinkId();
        e.ipInet = ipInet;
        e.userAgent = userAgent;
        e.requestId = null;
        e.sessionHash = null;
        e.details = data.details();
        return e;
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Column(name = "id", nullable = false)
        private Long id;

        @Column(name = "occurred_at", nullable = false)
        private Instant occurredAt;
    }
}
