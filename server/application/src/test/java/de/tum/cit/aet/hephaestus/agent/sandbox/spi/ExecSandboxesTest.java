package de.tum.cit.aet.hephaestus.agent.sandbox.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecSandboxesTest extends BaseUnitTest {

    private static final InputStream EMPTY = new ByteArrayInputStream(new byte[0]);
    private static final Duration MINUTE = Duration.ofMinutes(1);

    @Test
    void shouldAcceptPlainPathsInTheFolder() {
        assertThat(new ExecSandboxes.Add(List.of("inputs", "INDEX.md"), EMPTY, 0, MINUTE).names())
                .containsExactly("inputs", "INDEX.md");
        assertThat(new ExecSandboxes.Replace("repos/github.com/acme/api", EMPTY, 0, MINUTE).directory())
                .isEqualTo("repos/github.com/acme/api");
    }

    @Test
    void shouldRefuseAnAddedNameThatIsNotOneEntryOfTheFolder() {
        for (String name : List.of("", ".", "..", "inputs/context", "/etc", "a\0b")) {
            assertThatThrownBy(() -> new ExecSandboxes.Add(List.of(name), EMPTY, 0, MINUTE))
                    .as(name)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void shouldRefuseAReplacedDirectoryThatLeavesTheFolderOrIsTheFolder() {
        for (String directory : List.of("", "/workspace/repos", "repos/../..", "repos//api", "repos/./api", "repos/")) {
            assertThatThrownBy(() -> new ExecSandboxes.Replace(directory, EMPTY, 0, MINUTE))
                    .as(directory)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void shouldRefuseANegativeArchiveSize() {
        assertThatThrownBy(() -> new ExecSandboxes.Add(List.of("inputs"), EMPTY, -1, MINUTE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecSandboxes.Replace("repos", EMPTY, -1, MINUTE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
