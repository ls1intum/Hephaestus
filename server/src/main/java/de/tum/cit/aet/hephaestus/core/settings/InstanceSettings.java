package de.tum.cit.aet.hephaestus.core.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "silent_mode_engaged", nullable = false)
    private boolean silentModeEngaged;

    /** Why the brake was engaged; cleared on release. */
    @Column(name = "silent_mode_reason", length = 500)
    private String silentModeReason;

    @Column(name = "silent_mode_changed_at")
    private Instant silentModeChangedAt;

    /** Login of the admin who last flipped the brake (snapshot, not an FK — survives account deletion). */
    @Column(name = "silent_mode_changed_by", length = 255)
    private String silentModeChangedBy;
}
