package de.tum.cit.aet.hephaestus.core.settings;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.settings.spi.SilentModeQuery;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and updates the singleton {@link InstanceSettings} row. Unconditional bean (no runtime-role
 * gate): the server role serves the admin API, while the worker role consults
 * {@link SilentModeQuery} on every outbound delivery.
 */
@Service
@WorkspaceAgnostic("Singleton instance-wide settings row — no tenant dimension exists")
public class InstanceSettingsService implements SilentModeQuery {

    private static final Logger log = LoggerFactory.getLogger(InstanceSettingsService.class);

    private final InstanceSettingsRepository repository;

    InstanceSettingsService(InstanceSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public InstanceSettings get() {
        return repository.findById(InstanceSettings.SINGLETON_ID).orElseGet(this::seedSingleton);
    }

    /**
     * Production seeds the row via Liquibase; schemas built without migrations (tests use
     * {@code ddl-auto: create}) self-heal here. The insert is an idempotent {@code ON CONFLICT DO
     * NOTHING} upsert, so a concurrent seed is a no-op rather than a PK violation that would abort the
     * surrounding transaction — the subsequent read then always finds the row.
     */
    private InstanceSettings seedSingleton() {
        repository.insertSingletonIfAbsent(InstanceSettings.SINGLETON_ID);
        return repository
            .findById(InstanceSettings.SINGLETON_ID)
            .orElseThrow(() -> new IllegalStateException("instance_settings singleton absent after seed upsert"));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSilentModeEngaged() {
        // No self-heal on the delivery path: an absent row cannot carry an engaged brake, so absent = released.
        return repository
            .findById(InstanceSettings.SINGLETON_ID)
            .map(InstanceSettings::isSilentModeEngaged)
            .orElse(false);
    }

    /**
     * Flips the silent-mode brake. Idempotent full replacement of the silent-mode state (PUT
     * semantics — an emergency engage may be retried blindly). Logged at WARN in both directions so
     * the incident timeline is reconstructable from server logs alone.
     */
    @Transactional
    public InstanceSettings updateSilentMode(boolean engaged, @Nullable String reason, @Nullable String actor) {
        InstanceSettings settings = get();
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
        return settings;
    }
}
