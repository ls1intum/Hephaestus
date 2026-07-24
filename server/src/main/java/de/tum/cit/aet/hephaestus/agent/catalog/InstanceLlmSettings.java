package de.tum.cit.aet.hephaestus.agent.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.Nullable;

/**
 * Instance LLM settings singleton (#1368): egress allowlist, the workspace-BYO enable flag, and the
 * default unpriced-usage policy. GLOBAL. {@code id} is fixed to 1 (a DB CHECK enforces the singleton),
 * so it is never generated — the row is seeded by the changelog.
 */
@Entity
@Table(name = "instance_llm_settings")
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class InstanceLlmSettings {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false)
    private Short id;

    /** Comma/newline-delimited egress host allowlist; empty/null = allow all (non-breaking on upgrade). */
    @Nullable
    @Column(name = "allowed_egress_hosts", columnDefinition = "TEXT")
    private String allowedEgressHosts;

    @ColumnDefault("true")
    @Column(name = "allow_workspace_connections", nullable = false)
    private boolean allowWorkspaceConnections = true;

    @Nullable
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Nullable
    @Column(name = "updated_by", length = 255)
    private String updatedBy;
}
