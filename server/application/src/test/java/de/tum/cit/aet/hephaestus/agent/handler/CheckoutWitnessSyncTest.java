package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Cross-language sync test. The review session refuses the quotes of code that admission refuses as an unavailable
 * source ({@code validateEvidenceSources} in {@code pi-result-normalize.ts}): a repository citation names a captured
 * checkout witness, and a quote of the change needs the reviewed checkout captured.
 */
class CheckoutWitnessSyncTest extends BaseUnitTest {

    @Test
    void shouldNameTheCheckoutWitnessAlikeWhenTheSessionAndAdmissionCheckAQuoteOfCode() throws IOException {
        Path normalizer = Path.of("src/main/resources/agent/pi-result-normalize.ts");
        String source = Files.readString(
                Files.exists(normalizer)
                        ? normalizer
                        : Path.of("server/application").resolve(normalizer));
        Matcher witness = Pattern.compile("export const CHECKOUT_WITNESS = /\\^\\(\\?<root>(.+)\\)(.+)\\$/u;")
                .matcher(source);

        assertThat(witness.find())
                .as("pi-result-normalize.ts declares CHECKOUT_WITNESS")
                .isTrue();
        assertThat((witness.group(1) + witness.group(2)).replace("\\/", "/"))
                .isEqualTo(CandidateEvidenceVerifier.CHECKOUT_WITNESS.pattern());
        assertThat(source).contains("REVIEWED_CHECKOUT = \"" + SandboxLayout.REPO_MOUNT_RELATIVE + "\";");
        assertThat(source).contains("CAPTURED_REFS = \".git/" + GitRepositoryManager.CAPTURED_REFS + "\";");
    }
}
