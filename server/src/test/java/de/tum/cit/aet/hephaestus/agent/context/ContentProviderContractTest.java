package de.tum.cit.aet.hephaestus.agent.context;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces the {@link ContentProvider} contract (ADR: integration content = EXTRACT+LOAD of raw native
 * objects only; no practice-dependent Transform in a connector). A connector that names a practice or
 * pre-renders a observation is, by definition, code in the wrong layer — this test makes that structural.
 *
 * <p>Scope: the SCM-side providers under {@code agent.context.providers} (the mentor aspect providers under
 * {@code providers.mentor} are core-native projections, out of scope). The bright line that motivated this:
 * {@code TestPresenceContentProvider} / {@code BranchGraphContentProvider} computed practice-shaped features
 * from the mounted worktree and were deleted; they must never come back.
 */
class ContentProviderContractTest extends BaseUnitTest {

    private static final Path PROVIDERS_DIR = resolveDir(
        "src/main/java/de/tum/cit/aet/hephaestus/agent/context/providers",
        "server/src/main/java/de/tum/cit/aet/hephaestus/agent/context/providers"
    );

    @Test
    @DisplayName("no SCM content provider names a practice slug — connectors carry no practice-dependent logic")
    void noProviderNamesAPractice() throws IOException {
        Set<String> slugs = practiceSlugs();
        assertThat(slugs).as("catalogue slugs should load").isNotEmpty();

        for (Path provider : scmProviderSources()) {
            String src = Files.readString(provider, StandardCharsets.UTF_8);
            for (String slug : slugs) {
                assertThat(src)
                    .as(
                        "%s names the practice '%s' — a connector must not encode practice-dependent logic " +
                            "(move it to the precompute script or the agent)",
                        provider.getFileName(),
                        slug
                    )
                    .doesNotContain(slug);
            }
        }
    }

    @Test
    @DisplayName("the deleted worktree-derived feature providers must not return")
    void deletedFeatureProvidersStayDeleted() {
        for (String banned : List.of(
            "TestPresenceContentProvider",
            "BranchGraphContentProvider",
            "AcceptanceCriteriaContentProvider"
        )) {
            assertThat(PROVIDERS_DIR.resolve(banned + ".java"))
                .as("%s was deleted as worktree-derived Transform (or never built) — it must not reappear", banned)
                .doesNotExist();
        }
    }

    @Test
    @DisplayName("no SCM provider re-introduces a practice-shaped derived aggregate (supersession is the agent's job)")
    void noProviderEmitsADerivedAggregate() throws IOException {
        // ReviewThreadContentProvider once pre-computed a lossy supersession gate inside an EXTRACT+LOAD
        // connector (changesRequestedUnaddressed). It was removed in favour of raw rows + submittedAt so the
        // agent computes supersession. This guards the surviving providers against the aggregate creeping back.
        for (Path provider : scmProviderSources()) {
            String src = Files.readString(provider, StandardCharsets.UTF_8);
            for (String bannedAggregate : List.of("changesRequestedUnaddressed", "countUnaddressedChangesRequested")) {
                assertThat(src)
                    .as(
                        "%s re-introduces the derived '%s' aggregate — emit raw rows, let the agent judge",
                        provider.getFileName(),
                        bannedAggregate
                    )
                    .doesNotContain(bannedAggregate);
            }
        }
    }

    private static List<Path> scmProviderSources() throws IOException {
        try (Stream<Path> walk = Files.list(PROVIDERS_DIR)) {
            return walk.filter(p -> p.getFileName().toString().endsWith("ContentProvider.java")).toList();
        }
    }

    private static final Pattern SLUG = Pattern.compile("\"slug\"\\s*:\\s*\"([a-z0-9-]+)\"");

    private static Set<String> practiceSlugs() throws IOException {
        Set<String> slugs = new TreeSet<>();
        try (
            InputStream in = ContentProviderContractTest.class.getClassLoader().getResourceAsStream(
                "practices/default-catalog.json"
            )
        ) {
            String cat = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = SLUG.matcher(cat);
            while (m.find()) {
                slugs.add(m.group(1));
            }
        }
        return slugs;
    }

    private static Path resolveDir(String moduleRelative, String repoRelative) {
        Path candidate = Path.of(moduleRelative);
        return Files.isDirectory(candidate) ? candidate : Path.of(repoRelative);
    }
}
