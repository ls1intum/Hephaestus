package de.tum.cit.aet.hephaestus.core.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.Nullable;

/**
 * Singleton instance-wide settings row (id = {@link #SINGLETON_ID}). Deliberately not
 * workspace-scoped — listed in {@code WorkspaceScopedTables.GLOBAL_TABLES}.
 */
@Entity
@Table(name = "instance_settings")
@Getter
@Setter
@NoArgsConstructor
public class InstanceSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    @ColumnDefault("0")
    private long version;

    @Column(name = "silent_mode_engaged", nullable = false)
    @ColumnDefault("true")
    private boolean silentModeEngaged;

    /** Why the brake was engaged; cleared on release. */
    @Column(name = "silent_mode_reason", length = 500)
    private @Nullable String silentModeReason;

    @Column(name = "silent_mode_changed_at")
    private Instant silentModeChangedAt;

    /** Login of the admin who last flipped the brake (snapshot, not an FK — survives account deletion). */
    @Column(name = "silent_mode_changed_by", length = 255)
    private @Nullable String silentModeChangedBy;

    static InstanceSettings failSafeDefault() {
        InstanceSettings settings = new InstanceSettings();
        settings.setId(SINGLETON_ID);
        settings.setSilentModeEngaged(true);
        return settings;
    }
}
