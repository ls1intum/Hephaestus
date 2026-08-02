package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import org.jspecify.annotations.Nullable;

record PracticeAreaSnapshot(
    String slug,
    String name,
    @Nullable String description,
    boolean active,
    @Nullable String icon,
    @Nullable String color
) implements ConfigAuditSnapshot {
    static PracticeAreaSnapshot of(PracticeArea area) {
        return new PracticeAreaSnapshot(
            area.getSlug(),
            area.getName(),
            area.getDescription(),
            area.isActive(),
            area.getIcon(),
            area.getColor()
        );
    }
}
