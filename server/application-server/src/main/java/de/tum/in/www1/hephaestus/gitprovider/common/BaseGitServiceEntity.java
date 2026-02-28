package de.tum.in.www1.hephaestus.gitprovider.common;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Base class for all git service entities synced from external providers.
 * <p>
 * Provides:
 * <ul>
 *   <li>{@link #id} — Synthetic auto-generated primary key</li>
 *   <li>{@link #nativeId} — The provider's original numeric ID (always positive)</li>
 *   <li>{@link #provider} — FK to {@link GitProvider} identifying the provider instance</li>
 *   <li>{@link #createdAt} / {@link #updatedAt} — Audit timestamps from the provider</li>
 * </ul>
 * <p>
 * The combination of {@code (provider_id, native_id)} is unique per entity table,
 * replacing the previous approach of using provider-native IDs as primary keys
 * with negation for GitLab.
 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class BaseGitServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    protected Long id;

    /**
     * The provider's original numeric ID (always stored as a positive value).
     * <p>
     * Combined with {@link #provider}, this uniquely identifies the entity
     * across all provider instances.
     */
    @Column(name = "native_id", nullable = false)
    protected Long nativeId;

    protected Instant createdAt;

    protected Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    @ToString.Exclude
    protected GitProvider provider;
}
