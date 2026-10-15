package de.tum.cit.aet.hephaestus.agent.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.ClasspathSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.capture.ContentSource;
import de.tum.cit.aet.hephaestus.evidence.capture.MentorContextRequest;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class WorkspaceContextBuilderTest extends BaseUnitTest {
    private final MentorContextRequest request = new MentorContextRequest(1, 2, UUID.randomUUID(), null);

    private WorkspaceContextBuilder builder(ContentSource... sources) {
        return new WorkspaceContextBuilder(
                List.of(sources),
                List.of(),
                mock(ClasspathSourceCatalogRegistry.class),
                mock(JobFolderIndexBuilder.class));
    }

    private ContentSource source(String path, byte[] bytes) {
        return new ContentSource() {
            @Override
            public boolean supports(MentorContextRequest request) {
                return true;
            }

            @Override
            public void contribute(MentorContextRequest request, Map<String, byte[]> files) {
                files.put(path, bytes);
            }
        };
    }

    @Test
    void stagesAnIndependentCopyOfEachSourceFile() {
        byte[] bytes = {1, 2};
        var captured = builder(source("inputs/context/one.json", bytes)).build(request);
        bytes[0] = 9;
        assertThat(captured.get("inputs/context/one.json")).containsExactly((byte) 1, (byte) 2);
    }

    @Test
    void rejectsCollidingSourcesAndPathsOutsideTheDeclaredRoot() {
        assertThatThrownBy(() -> builder(
                                source("inputs/context/one.json", new byte[0]),
                                source("inputs/context/one.json", new byte[0]))
                        .build(request))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> builder(source("outside.json", new byte[0])).build(request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void preservesOtherContextWhenAnOptionalSourceFails() {
        ContentSource failed = new ContentSource() {
            @Override
            public boolean supports(MentorContextRequest request) {
                return true;
            }

            @Override
            public boolean required() {
                return false;
            }

            @Override
            public void contribute(MentorContextRequest request, Map<String, byte[]> files) {
                throw new IllegalStateException("unavailable");
            }
        };
        assertThat(builder(failed, source("inputs/context/available.json", new byte[0]))
                        .build(request))
                .containsOnlyKeys("inputs/context/available.json");
    }

    @Test
    void shouldReadOnlyAResourceOwnedByOneInstalledSource() {
        var reader = mock(ContentSource.class);
        var path = "inputs/context/merge_readiness/17.json";
        var detail = JsonMapper.builder().build().createObjectNode().put("status", "FOUND");
        when(reader.read(1, 2, path)).thenReturn(Optional.of(detail));
        var contexts = builder(reader, mock(ContentSource.class));
        assertThat(contexts.read(1, 2, path)).contains(detail);
        assertThat(contexts.read(1, 2, "inputs/context/unknown.json")).isEmpty();
    }

    @Test
    void shouldRejectAResourceClaimedByTwoSources() {
        var first = mock(ContentSource.class);
        var second = mock(ContentSource.class);
        var path = "inputs/context/merge_readiness/17.json";
        var detail = JsonMapper.builder().build().createObjectNode().put("status", "FOUND");
        when(first.read(1, 2, path)).thenReturn(Optional.of(detail));
        when(second.read(1, 2, path)).thenReturn(Optional.of(detail));
        assertThatThrownBy(() -> builder(first, second).read(1, 2, path)).isInstanceOf(IllegalStateException.class);
    }
}
