package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "curated_area_override")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CuratedAreaOverride {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "slug", nullable = false, length = 64, updatable = false)
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

    @Column(name = "based_on_digest", length = 128)
    private @Nullable String acceptedBundledDigest;

    @Column(name = "retired_at")
    private @Nullable Instant retiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private @Nullable Long version;

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

    public void write(AreaDefinition definition, @Nullable String acceptedBundledDigest, Instant now) {
        this.name = definition.name();
        this.description = definition.description();
        this.icon = definition.icon();
        this.color = definition.color();
        this.acceptedBundledDigest = acceptedBundledDigest;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void clearDefinition(Instant now) {
        this.name = null;
        this.description = null;
        this.icon = null;
        this.color = null;
        this.acceptedBundledDigest = null;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void acknowledge(@Nullable String shippedDigest, Instant now) {
        this.acceptedBundledDigest = shippedDigest;
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

    public void clearPosition(Instant now) {
        this.position = null;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public boolean isEmpty() {
        return definition() == null && retiredAt == null && position == null;
    }
}
