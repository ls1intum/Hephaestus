package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import org.jspecify.annotations.Nullable;

record PracticeAreaSnapshot(
    String slug,
    String name,
    @Nullable String description,
    boolean visibleInPracticeDashboards,
    @Nullable String icon,
    @Nullable String color,
    /** Null means the area holds no autonomy and follows the workspace default — a real state, so it is serialized. */
    @Nullable PracticeAutonomy autonomy
) implements ConfigAuditSnapshot {
    static PracticeAreaSnapshot of(PracticeArea area) {
        return new PracticeAreaSnapshot(
            area.getSlug(),
            area.getName(),
            area.getDescription(),
            area.isVisibleInPracticeDashboards(),
            area.getIcon(),
            area.getColor(),
            area.getAutonomy()
        );
    }
}
