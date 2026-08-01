package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** What an administrator said about one area. Exactly the shape of {@link CuratedPracticeOverride}. */
@Entity
@Table(name = "curated_area_override")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CuratedAreaOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "slug", nullable = false, unique = true, length = 64, updatable = false)
    private String slug;

    @Column(name = "name", length = 128)
    private @Nullable String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private @Nullable String description;

    @Column(name = "position")
    private @Nullable Integer position;

    @Column(name = "icon", length = 64)
    private @Nullable String icon;

    @Column(name = "color", length = 32)
    private @Nullable String color;

    @Column(name = "based_on_digest", length = 64)
    private @Nullable String basedOnDigest;

    @Column(name = "retired_at")
    private @Nullable Instant retiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public CuratedAreaOverride(String slug, Instant now) {
        this.slug = Objects.requireNonNull(slug, "slug");
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public @Nullable AreaDefinition definition() {
        if (name == null) {
            return null;
        }
        return new AreaDefinition(name, description, icon, color);
    }

    public void write(AreaDefinition definition, @Nullable String basedOnDigest, Instant now) {
        this.name = definition.name();
        this.description = definition.description();
        this.icon = definition.icon();
        this.color = definition.color();
        this.basedOnDigest = basedOnDigest;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void clearDefinition(Instant now) {
        this.name = null;
        this.description = null;
        this.icon = null;
        this.color = null;
        this.basedOnDigest = null;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void acknowledge(@Nullable String shippedDigest, Instant now) {
        this.basedOnDigest = shippedDigest;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void setStatus(CuratedStatus status, Instant now) {
        this.retiredAt = status == CuratedStatus.RETIRED ? now : null;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void setPosition(int position, Instant now) {
        this.position = position;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public boolean isEmpty() {
        return definition() == null && retiredAt == null && position == null;
    }
}
