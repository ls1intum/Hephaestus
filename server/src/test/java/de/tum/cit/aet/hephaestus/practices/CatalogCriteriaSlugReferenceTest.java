package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Anti-drift guard for cross-practice slug references inside the catalogue's {@code criteria} prose.
 *
 * <p>Several practices route a sibling concern to another practice by NAME ("(that is X)", "(deferred to
 * X)", "X owns it"). The live evaluation found criteria naming a slug that is not a real practice — a
 * phantom (e.g. {@code keeps-secrets-out}) or a truncation of a real slug (e.g.
 * {@code validates-inputs-and-edge-cases} for {@code validates-inputs-and-edge-cases-at-the-boundary}).
 * The LLM dutifully routes the finding to a slug that does not exist, so the concern silently vanishes.
 * Criteria prose is not type-checked, so this test is the contract: every slug-shaped reference in the
 * criteria must resolve to a real practice slug (the synthetic {@code hardcoded-secrets} sentinel
 * excepted), and no truncation of a real slug may appear.
 */
class CatalogCriteriaSlugReferenceTest extends BaseUnitTest {

    /**
     * The one slug-shaped token the criteria are permitted to name that is NOT a catalogue practice: the
     * synthetic finding-fingerprint sentinel used by the secret-detector discipline.
     */
    private static final Set<String> SYNTHETIC_SLUGS = Set.of("hardcoded-secrets");

    /** A practice slug is a lowercase, hyphen-joined token of three or more segments. */
    private static final Pattern SLUG_SHAPED = Pattern.compile("\\b[a-z]+(?:-[a-z]+){2,}\\b");

    @Test
    @DisplayName("no criteria string names a slug that isn't a real practice slug (truncations and phantoms)")
    void everyCriteriaSlugReferenceResolvesToARealPractice() throws IOException {
        Map<String, JsonNode> practices = loadPractices();
        Set<String> realSlugs = new TreeSet<>(practices.keySet());

        Set<String> offenders = new TreeSet<>();
        for (Map.Entry<String, JsonNode> e : practices.entrySet()) {
            String criteria = e.getValue().path("criteria").asText("");
            Matcher m = SLUG_SHAPED.matcher(criteria);
            while (m.find()) {
                String token = m.group();
                if (realSlugs.contains(token) || SYNTHETIC_SLUGS.contains(token)) {
                    continue;
                }
                // Only flag tokens that look like a real slug reference: a segment-prefix truncation of an
                // actual practice slug. A plain English compound (e.g. "end-to-end", "line-by-line") is not a
                // prefix of any practice slug, so it is left alone — this keeps the guard free of false
                // positives while still catching the dangerous "drops the tail of a real slug" mistake.
                String truncationOf = realSlugTruncatedBy(token, realSlugs);
                if (truncationOf != null) {
                    offenders.add(token + " (in " + e.getKey() + " -> should be " + truncationOf + ")");
                }
            }
        }

        assertThat(offenders)
            .as(
                "every slug-shaped criteria reference must name a real practice slug; these are truncations of a real slug"
            )
            .isEmpty();

        // Belt-and-braces: the phantom slugs the live evaluation actually mis-routed to must never reappear,
        // even though they are not prefixes of any real slug (so the truncation check above would miss them).
        // Match them only as whole slug tokens (not followed by another '-' segment) so a real slug that
        // legitimately extends one of them (e.g. avoids-unsafe-panics-and-chosen-crashes) is not flagged.
        String rawCatalogue = readCatalogue();
        for (String phantom : Set.of("keeps-secrets-out", "avoids-unsafe-panics", "exposed-credential-material")) {
            Pattern asWholeSlug = Pattern.compile(Pattern.quote(phantom) + "(?![a-z-])");
            assertThat(asWholeSlug.matcher(rawCatalogue).find())
                .as("the phantom slug '%s' must not be named in any criteria", phantom)
                .isFalse();
        }
    }

    /** Returns the real slug that {@code token} is a strict segment-prefix of, or {@code null}. */
    private static String realSlugTruncatedBy(String token, Set<String> realSlugs) {
        String prefix = token + "-";
        for (String slug : realSlugs) {
            if (slug.startsWith(prefix)) {
                return slug;
            }
        }
        return null;
    }

    private static Map<String, JsonNode> loadPractices() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(readCatalogue());
        Map<String, JsonNode> practices = new LinkedHashMap<>();
        for (JsonNode area : root.path("areas")) {
            for (JsonNode practice : area.path("practices")) {
                practices.put(practice.path("slug").asText(), practice);
            }
        }
        assertThat(practices).as("catalogue must declare practices").isNotEmpty();
        return practices;
    }

    private static String readCatalogue() throws IOException {
        try (
            InputStream in = CatalogCriteriaSlugReferenceTest.class.getClassLoader().getResourceAsStream(
                "practices/default-catalog.json"
            )
        ) {
            assertThat(in).as("practices/default-catalog.json must be on the classpath").isNotNull();
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
