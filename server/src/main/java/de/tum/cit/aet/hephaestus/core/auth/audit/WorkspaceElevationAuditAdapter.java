package de.tum.cit.aet.hephaestus.core.auth.audit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.tum.cit.aet.hephaestus.core.auth.spi.WorkspaceElevationAudit;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * In-{@code core.auth} implementation of {@link WorkspaceElevationAudit}, mirroring
 * {@link ResearchConsentAuditAdapter}.
 *
 * <p>Writes are de-duplicated per {@code (account, workspace)} window, so the per-request elevation
 * decision in {@code WorkspaceContextFilter} marks elevated access <em>sessions</em>, not every HTTP
 * request. The window is in-memory and per-pod, which can only ever add rows, never drop them.
 */
@ConditionalOnServerRole
@Component
public class WorkspaceElevationAuditAdapter implements WorkspaceElevationAudit {

    /** One row per (account, workspace) per window — enough resolution for the audit trail. */
    static final Duration DEDUP_WINDOW = Duration.ofMinutes(15);

    private final AuthEventLogger authEventLogger;
    private final Cache<String, Boolean> recentlyRecorded = Caffeine.newBuilder()
        .expireAfterWrite(DEDUP_WINDOW)
        .maximumSize(10_000)
        .build();

    public WorkspaceElevationAuditAdapter(AuthEventLogger authEventLogger) {
        this.authEventLogger = authEventLogger;
    }

    @Override
    public void recordElevatedAccess(long accountId, long workspaceId) {
        String key = accountId + ":" + workspaceId;
        if (recentlyRecorded.getIfPresent(key) != null) {
            return;
        }
        boolean recorded = authEventLogger
            .event(AuthEvent.EventType.WORKSPACE_ELEVATION, AuthEvent.Result.SUCCESS)
            .account(accountId)
            .workspace(workspaceId)
            .record();
        // Claim the window only on a persisted row: record() swallows its failures, so claiming
        // unconditionally would suppress every retry for the rest of the window — the one way this
        // could log fewer rows than accesses. Retrying costs at most a duplicate row.
        if (recorded) {
            recentlyRecorded.put(key, Boolean.TRUE);
        }
    }
}
