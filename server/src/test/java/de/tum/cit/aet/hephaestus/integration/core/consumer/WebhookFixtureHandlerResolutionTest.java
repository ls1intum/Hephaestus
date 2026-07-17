package de.tum.cit.aet.hephaestus.integration.core.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.handler.AbstractIntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.github.webhook.GithubSubjectKeyDeriver;
import de.tum.cit.aet.hephaestus.integration.scm.github.webhook.GithubSubjectParser;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Mechanical dispatcher round-trip guard. For every committed webhook fixture whose event the system
 * <em>claims to handle</em>, the real routing pipeline
 * (fixture payload → {@code SubjectKeyDeriver} → {@code SubjectParser} → {@link EventTypeKey} →
 * handler-registry lookup) MUST resolve a registered handler. A key that no handler owns is
 * ACK-dropped silently at DEBUG by {@code IntegrationNatsConsumer} — an invisible data-loss bug. This
 * test makes that whole class fail loudly at build time and names the offending fixture.
 *
 * <p>Unlike per-side unit tests (deriver-only, parser-only), and unlike {@link SubjectGrammarRoundTripTest}
 * (which only proves the publisher subject falls under the consumer's <em>subscription filter</em>), this
 * closes the last link: the parsed {@link EventTypeKey} actually resolves a handler bean. It would have
 * caught the {@code repository.team} dead branch (team↔repo permission events keyed to a tier with no
 * handler) and the GitLab group-event dead code.
 *
 * <p><b>Ground truth without a Spring context.</b> The set of registered keys is read straight from the
 * production handler classes: every concrete {@link IntegrationMessageHandler} under the kind's package
 * is reflectively constructed with null collaborators (their constructors only assign fields; the
 * {@code EventTypeKey} is built from compile-time literals in the {@code super(...)} call) and its
 * {@link IntegrationMessageHandler#key()} recorded. No database, no beans — but zero drift from reality.
 *
 * <p><b>"Claims to handle".</b> A fixture is only required to resolve when its event token is the suffix
 * of some registered key — i.e. the system genuinely handles that event type on <em>some</em> tier.
 * Fixtures for events we deliberately ignore (e.g. {@code deployment}, {@code star}, {@code watch}) have
 * no handler on any tier and are skipped, so this never produces a false failure. The bug it catches is
 * the <em>partial</em> case: an event handled on one tier but silently dropped on another (exactly
 * {@code team}: {@code organization.team} handled, {@code repository.team} was not).
 *
 * <p><b>Extension point.</b> Only GitHub is asserted here today. The GitLab group-event routing gap is
 * being fixed in parallel under {@code scm/gitlab}; once that lands, add a {@code gitlab} block that
 * feeds {@code GitlabSubjectKeyDeriver}/{@code GitlabSubjectParser} with the event token sourced from
 * {@code object_kind} (Slack/Outline analogously from their event fields). The
 * {@link #resolutionFailures} helper is kind-agnostic; only the per-kind event-token extraction differs.
 */
class WebhookFixtureHandlerResolutionTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String GITHUB_HANDLER_PACKAGE = "de.tum.cit.aet.hephaestus.integration.scm.github";
    private static final Path GITHUB_FIXTURE_DIR = Paths.get("src/test/resources/github");

    private static final GithubSubjectKeyDeriver GITHUB_DERIVER = new GithubSubjectKeyDeriver();
    private static final GithubSubjectParser GITHUB_PARSER = new GithubSubjectParser();

    @Test
    void everyClaimedGithubFixtureResolvesAHandler() throws IOException {
        Set<EventTypeKey> registeredKeys = registeredKeys(GITHUB_HANDLER_PACKAGE);

        // Guard-health: if the registry came back empty the reflective scan silently broke and every
        // assertion below would vacuously pass. Anchor on the two tiers this fix touches.
        assertThat(registeredKeys)
            .as("GitHub handler registry must be discoverable and non-empty")
            .isNotEmpty()
            .contains(new EventTypeKey(IntegrationKind.GITHUB, "organization.team"))
            .contains(new EventTypeKey(IntegrationKind.GITHUB, "repository.team"));

        // Event tokens the system claims to handle = the last segment of every registered key
        // ("repository.issues" -> "issues", "organization.team" -> "team").
        Set<String> claimedEventTokens = registeredKeys
            .stream()
            .map(k -> lastSegment(k.eventType()))
            .collect(Collectors.toCollection(TreeSet::new));

        List<String> failures = resolutionFailures(
            GITHUB_FIXTURE_DIR,
            registeredKeys,
            claimedEventTokens,
            WebhookFixtureHandlerResolutionTest::githubEventToken,
            (payload, eventToken) ->
                GITHUB_PARSER.parse(GITHUB_DERIVER.deriveSubject(payload, Map.of("X-GitHub-Event", eventToken)))
        );

        assertThat(failures)
            .as(
                "Every committed GitHub webhook fixture for a claimed event must round-trip to a " +
                    "registered handler. A listed fixture derives a subject whose parsed EventTypeKey has " +
                    "no handler — the consumer would ACK-drop it silently (invisible data loss)."
            )
            .isEmpty();
    }

    // --- pipeline ---

    /** Kind-agnostic: derive the {@link EventTypeKey} the routing pipeline would compute for a fixture. */
    @FunctionalInterface
    private interface KeyForFixture {
        EventTypeKey apply(JsonNode payload, String eventToken);
    }

    private static List<String> resolutionFailures(
        Path fixtureDir,
        Set<EventTypeKey> registeredKeys,
        Set<String> claimedEventTokens,
        java.util.function.Function<Path, String> eventTokenOf,
        KeyForFixture keyForFixture
    ) throws IOException {
        List<String> failures = new ArrayList<>();
        if (!Files.isDirectory(fixtureDir)) {
            return failures;
        }
        List<Path> fixtures;
        try (var stream = Files.list(fixtureDir)) {
            fixtures = stream
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .sorted()
                .toList();
        }
        for (Path fixture : fixtures) {
            String eventToken = eventTokenOf.apply(fixture);
            // Only events the system claims to handle on some tier are required to resolve.
            if (!claimedEventTokens.contains(eventToken)) {
                continue;
            }
            JsonNode payload = MAPPER.readTree(Files.readAllBytes(fixture));
            EventTypeKey key;
            try {
                key = keyForFixture.apply(payload, eventToken);
            } catch (RuntimeException e) {
                failures.add(fixture.getFileName() + " -> parser rejected derived subject: " + e.getMessage());
                continue;
            }
            if (!registeredKeys.contains(key)) {
                failures.add(fixture.getFileName() + " (event=" + eventToken + ") -> no handler for key " + key);
            }
        }
        return failures;
    }

    /** GitHub's {@code X-GitHub-Event} equals the fixture filename's first dot-delimited segment. */
    private static String githubEventToken(Path fixture) {
        String name = fixture.getFileName().toString();
        if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - ".json".length());
        }
        int dot = name.indexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    // --- registry ground truth (no Spring context) ---

    private static Set<EventTypeKey> registeredKeys(String packageToScan) {
        var provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AssignableTypeFilter(IntegrationMessageHandler.class));

        Set<EventTypeKey> keys = new java.util.HashSet<>();
        List<String> introspectionFailures = new ArrayList<>();
        for (var candidate : provider.findCandidateComponents(packageToScan)) {
            String className = candidate.getBeanClassName();
            if (className == null) {
                continue;
            }
            try {
                Class<?> clazz = Class.forName(className);
                if (
                    Modifier.isAbstract(clazz.getModifiers()) || clazz.equals(AbstractIntegrationMessageHandler.class)
                ) {
                    continue;
                }
                IntegrationMessageHandler handler = instantiateForKey(clazz);
                EventTypeKey key = handler.key();
                if (key == null) {
                    introspectionFailures.add(className + " returned null key()");
                } else {
                    keys.add(key);
                }
            } catch (Throwable t) {
                introspectionFailures.add(className + " -> " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }

        // A handler we cannot introspect is a hole in the guard — fail loudly rather than under-cover.
        assertThat(introspectionFailures)
            .as("Every %s handler must be reflectively introspectable to read its routing key", packageToScan)
            .isEmpty();
        return keys;
    }

    /**
     * Construct a handler purely to read {@link IntegrationMessageHandler#key()}. Handler constructors
     * only assign their collaborators to fields and pass compile-time literals to {@code super(...)}, so
     * null collaborators are safe here — {@code key()} depends solely on those literals.
     */
    private static IntegrationMessageHandler instantiateForKey(Class<?> clazz) throws ReflectiveOperationException {
        Constructor<?> ctor = java.util.Arrays.stream(clazz.getDeclaredConstructors())
            .max(Comparator.comparingInt(Constructor::getParameterCount))
            .orElseThrow(() -> new NoSuchMethodException("no constructor on " + clazz.getName()));
        ctor.setAccessible(true);
        Object[] args = new Object[ctor.getParameterCount()];
        return (IntegrationMessageHandler) ctor.newInstance(args);
    }

    private static String lastSegment(String eventType) {
        int dot = eventType.lastIndexOf('.');
        return dot < 0 ? eventType : eventType.substring(dot + 1);
    }
}
