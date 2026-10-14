package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.evidence.capture.CaptureContribution;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScmCaptureContributionsTest extends BaseUnitTest {
    @Test
    void shouldCloseEveryResourceAndKeepEveryFailure() {
        var closed = new ArrayList<Integer>();
        var first = new IOException("first");
        var second = new IOException("second");
        var contributions = List.of(
                contribution(() -> {
                    closed.add(1);
                    throw first;
                }),
                contribution(() -> {
                    closed.add(2);
                    throw second;
                }),
                contribution(() -> closed.add(3)));
        assertThatThrownBy(() -> ScmCaptureContributions.closeAll(contributions))
                .isSameAs(first)
                .hasSuppressedException(second);
        assertThat(closed).containsExactly(1, 2, 3);
    }

    private static CaptureContribution contribution(AutoCloseable cleanup) {
        return new CaptureContribution(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), cleanup, Map.of(),
                List.of(), List.of(), List.of());
    }
}
