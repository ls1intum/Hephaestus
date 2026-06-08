package de.tum.cit.aet.hephaestus.testconfig;

import de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.integration.scm.github.events.GitHubProjectEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Shared recorder for SCM domain events in integration tests. Imported once by
 * {@link BaseIntegrationTest}, so handler/processor tests do not each declare their own
 * {@code @Component} listener — that gave every such class a distinct Spring context cache key and
 * forced a fresh (Testcontainers-backed) context boot per class. Tests record via the shared bean and
 * read back with {@link #ofType(Class)}; {@link #clear()} between tests.
 *
 * <p>Passive recorder only (no side effects), so it never alters event-publish semantics.
 */
@Component
public class RecordingScmEventListener {

    private final List<Object> events = new CopyOnWriteArrayList<>();

    @EventListener
    void onScmEvent(ScmDomainEvent.Event event) {
        events.add(event);
    }

    @EventListener
    void onProjectEvent(GitHubProjectEvent.ProjectEvent event) {
        events.add(event);
    }

    @EventListener
    void onProjectItemEvent(GitHubProjectEvent.ProjectItemEvent event) {
        events.add(event);
    }

    @EventListener
    void onProjectStatusUpdateEvent(GitHubProjectEvent.ProjectStatusUpdateEvent event) {
        events.add(event);
    }

    /** All recorded events of the given concrete type, in publish order. */
    public <T> List<T> ofType(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /** Clear recorded events — call in {@code @BeforeEach}. */
    public void clear() {
        events.clear();
    }
}
