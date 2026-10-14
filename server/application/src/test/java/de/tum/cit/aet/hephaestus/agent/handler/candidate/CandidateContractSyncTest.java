package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.spi.CandidateRefusal;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Cross-language sync test. The practice host refuses a candidate with {@code parseCandidate} in {@code lib/contract.ts}
 * before it submits it, and admission refuses it again with {@link CandidateParser}. A bound or a refusal order that
 * differs lets one side accept what the other refuses, or name another reason for the same value.
 */
class CandidateContractSyncTest extends BaseUnitTest {

    private static final Pattern LIMITS = Pattern.compile("export const CANDIDATE_LIMITS = \\{([^}]*)}");
    private static final Pattern LIMIT = Pattern.compile("(\\w+):\\s*([\\d_]+)");
    private static final Pattern REFUSALS = Pattern.compile("export const CANDIDATE_REFUSALS = \\[([^\\]]*)]");
    private static final Pattern QUOTED = Pattern.compile("\"([a-z_]+)\"");
    private static final Pattern QUOTED_ANY = Pattern.compile("\"([^\"]+)\"");

    @Test
    void shouldBoundCandidatesAlikeWhenTheHostAndAdmissionReadThem() throws IOException {
        Matcher limits = LIMITS.matcher(contract());
        assertThat(limits.find()).as("contract.ts declares CANDIDATE_LIMITS").isTrue();
        Map<String, Integer> runner = LIMIT.matcher(limits.group(1))
                .results()
                .collect(Collectors.toMap(
                        limit -> upperSnake(limit.group(1)),
                        limit -> Integer.parseInt(limit.group(2).replace("_", ""))));

        Map<String, Integer> server = Arrays.stream(CandidateLimits.class.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
                .collect(Collectors.toMap(Field::getName, CandidateContractSyncTest::intValue));

        assertThat(runner).isNotEmpty().isEqualTo(server);
    }

    @Test
    void shouldRefuseInOneOrderWhenTheHostAndAdmissionNameARefusal() throws IOException {
        Matcher refusals = REFUSALS.matcher(contract());
        assertThat(refusals.find())
                .as("contract.ts declares CANDIDATE_REFUSALS")
                .isTrue();
        List<String> runner = QUOTED.matcher(refusals.group(1))
                .results()
                .map(code -> code.group(1))
                .toList();

        assertThat(runner)
                .containsExactlyElementsOf(Arrays.stream(CandidateRefusal.values())
                        .filter(value -> value != CandidateRefusal.BASIS_STATE_UNSUPPORTED
                                && value != CandidateRefusal.BASIS_REVISION_UNSUPPORTED
                                && value != CandidateRefusal.QUOTE_UNVERIFIED
                                && value != CandidateRefusal.SOURCE_UNAVAILABLE
                                && value != CandidateRefusal.BASIS_WITNESS_MISSING
                                && value != CandidateRefusal.DUTY_QUOTE_UNVERIFIED)
                        .map(CandidateRefusal::wireValue)
                        .toList());
    }

    @Test
    void shouldNameOmissionReasonsAlikeWhenTheHostAndAdmissionReadCoverage() throws IOException {
        String contract = contract();
        Matcher reasons =
                Pattern.compile("export const REASONS = \\[([^\\]]*)]").matcher(contract);
        assertThat(reasons.find()).as("contract.ts declares REASONS").isTrue();
        Matcher omissions =
                Pattern.compile("export const OMISSION_REASONS = \\[([^\\]]*)]").matcher(contract);
        assertThat(omissions.find()).as("contract.ts declares OMISSION_REASONS").isTrue();
        List<String> runner = new ArrayList<>();
        Pattern.compile("\"([^\"]+)\"|\\.\\.\\.REASONS")
                .matcher(omissions.group(1))
                .results()
                .forEach(item ->
                        runner.addAll(item.group(1) == null ? quoted(reasons.group(1)) : List.of(item.group(1))));

        assertThat(runner).containsExactlyElementsOf(CandidateParser.OMISSION_REASONS);
    }

    private static List<String> quoted(String text) {
        return QUOTED_ANY.matcher(text).results().map(code -> code.group(1)).toList();
    }

    private static String upperSnake(String camelCase) {
        return camelCase.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    private static int intValue(Field field) {
        try {
            return field.getInt(null);
        } catch (IllegalAccessException inaccessible) {
            throw new IllegalStateException(field.getName(), inaccessible);
        }
    }

    private static String contract() throws IOException {
        return Files.readString(resolveRepoFile("docker/agents/practice-scripts/lib/core/contract.ts"));
    }

    private static Path resolveRepoFile(String relativePath) {
        Path candidate = Path.of("..", "..").resolve(relativePath);
        return Files.exists(candidate) ? candidate : Path.of(relativePath);
    }
}
