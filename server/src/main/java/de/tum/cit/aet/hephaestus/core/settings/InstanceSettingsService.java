package de.tum.cit.aet.hephaestus.core.settings;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.security.SecurityUtils;
import de.tum.cit.aet.hephaestus.core.settings.spi.SilentModeQuery;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Boots on every runtime role: the server serves the admin API, the worker consults
 * {@link SilentModeQuery} on every outbound delivery. It therefore must not hard-require a
 * server-only collaborator — {@link AuthEventLogger} is {@code @ConditionalOnServerRole} and is
 * absent on a worker-only pod, so it is injected as an {@link java.util.Optional}.
 */
@Service
@WorkspaceAgnostic("Singleton instance-wide settings row — no tenant dimension exists")
public class InstanceSettingsService implements SilentModeQuery {

    private static final Logger log = LoggerFactory.getLogger(InstanceSettingsService.class);

    private final InstanceSettingsRepository repository;
    private final Optional<AuthEventLogger> authEventLogger;
    private final ObjectMapper objectMapper;

    InstanceSettingsService(
        InstanceSettingsRepository repository,
        Optional<AuthEventLogger> authEventLogger,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.authEventLogger = authEventLogger;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public InstanceSettings get() {
        return repository.findById(InstanceSettings.SINGLETON_ID).orElseGet(InstanceSettings::new);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSilentModeEngaged() {
        return repository
            .findById(InstanceSettings.SINGLETON_ID)
            .map(InstanceSettings::isSilentModeEngaged)
            .orElse(false);
    }

    /** Logged at WARN in both directions so the incident timeline is reconstructable from logs alone. */
    @Transactional
    public InstanceSettings updateSilentMode(boolean engaged, @Nullable String reason, @Nullable String actor) {
        InstanceSettings settings = repository
            .findById(InstanceSettings.SINGLETON_ID)
            .orElseGet(() -> {
                InstanceSettings row = new InstanceSettings();
                row.setId(InstanceSettings.SINGLETON_ID);
                return row;
            });
        String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();
        settings.setSilentModeEngaged(engaged);
        settings.setSilentModeReason(engaged ? trimmedReason : null);
        settings.setSilentModeChangedAt(Instant.now());
        settings.setSilentModeChangedBy(actor);
        log.warn(
            "Instance silent mode {}: actor={}, reason={}",
            engaged ? "ENGAGED — all outbound delivery suppressed" : "RELEASED — outbound delivery resumed",
            actor,
            trimmedReason
        );
        InstanceSettings saved = repository.save(settings);
        // auth_event, not config_audit_event: the latter's workspace_id is NOT NULL and this is instance-level.
        // The writer commits in its own transaction, so the trail survives a rollback of this one — an
        // over-recorded toggle beats a silently unaudited one for a control this consequential.
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("engaged", engaged);
        if (trimmedReason != null) {
            details.put("reason", trimmedReason);
        }
        authEventLogger.ifPresent(logger ->
            logger
                .event(AuthEvent.EventType.SILENT_MODE_CHANGED, AuthEvent.Result.SUCCESS)
                .actingAccount(SecurityUtils.getCurrentAccountId().orElse(null))
                .details(objectMapper.writeValueAsString(details))
                .record()
        );
        return saved;
    }
}
