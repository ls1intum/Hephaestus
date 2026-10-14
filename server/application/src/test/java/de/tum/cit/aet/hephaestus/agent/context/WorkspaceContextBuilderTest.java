package de.tum.cit.aet.hephaestus.agent.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.evidence.ClasspathSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.capture.ContentSource;
import de.tum.cit.aet.hephaestus.evidence.capture.MentorContextRequest;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
}
