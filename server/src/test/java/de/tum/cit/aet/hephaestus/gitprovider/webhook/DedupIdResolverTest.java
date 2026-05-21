package de.tum.cit.aet.hephaestus.gitprovider.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

/** Port of {@code webhook-ingest/test/utils/dedupe.test.ts}. Contract: {@code prefix-{32-hex-chars}}. */
class DedupIdResolverTest extends BaseUnitTest {

    @Test
    void produces32CharLowercaseHexAfterPrefix() {
        String id = DedupIdResolver.build("github", new byte[] { 1, 2, 3, 4, 5 }, "push");
        assertThat(id).matches("^github-[a-f0-9]{32}$");
    }

    @Test
    void deterministicForSameInputs() {
        byte[] body = { 10, 20, 30, 40 };
        assertThat(DedupIdResolver.build("gitlab", body, "event")).isEqualTo(
            DedupIdResolver.build("gitlab", body, "event")
        );
    }

    @Test
    void changesWhenExtraChanges() {
        byte[] body = { 5, 6, 7 };
        assertThat(DedupIdResolver.build("github", body, "push")).isNotEqualTo(
            DedupIdResolver.build("github", body, "pull_request")
        );
    }

    @Test
    void emptyExtraEqualsNoExtra() {
        byte[] body = { 1, 2, 3 };
        assertThat(DedupIdResolver.build("github", body, ""))
            .isEqualTo(DedupIdResolver.build("github", body, null))
            .isEqualTo(DedupIdResolver.build("github", body));
    }
}
