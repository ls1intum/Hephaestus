package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

// Direct-call caps must match across languages so the runner and proxy refuse the same oversized calls.
class PracticeScriptCapsSyncTest extends BaseUnitTest {

    private static final Pattern CAPS = Pattern.compile("MODEL_SLOT_CAPS = \\{([^}]*)}");
    private static final Pattern CAP = Pattern.compile("(\\w+):\\s*([\\d_]+)");

    @Test
    void shouldCapEachPracticeScriptCallInTheRunnerAsTheProxyDoes() throws IOException {
        Matcher caps =
                CAPS.matcher(Files.readString(resolveRepoFile("docker/agents/practice-scripts/lib/core/contract.ts")));
        assertThat(caps.find()).as("contract.ts declares MODEL_SLOT_CAPS").isTrue();
        Map<String, Integer> runner = CAP.matcher(caps.group(1))
                .results()
                .collect(Collectors.toMap(
                        cap -> cap.group(1),
                        cap -> Integer.parseInt(cap.group(2).replace("_", ""))));

        assertThat(runner)
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        ModelKind.DECISION.name().toLowerCase(Locale.ROOT), LlmProxyService.MAX_DECISION_QUESTIONS,
                        ModelKind.EMBEDDING.name().toLowerCase(Locale.ROOT), LlmProxyService.MAX_EMBEDDING_INPUTS,
                        ModelKind.RERANKING.name().toLowerCase(Locale.ROOT), LlmProxyService.MAX_RERANK_DOCUMENTS));
    }

    private static Path resolveRepoFile(String relativePath) {
        Path candidate = Path.of("..", "..").resolve(relativePath);
        return Files.exists(candidate) ? candidate : Path.of(relativePath);
    }
}
