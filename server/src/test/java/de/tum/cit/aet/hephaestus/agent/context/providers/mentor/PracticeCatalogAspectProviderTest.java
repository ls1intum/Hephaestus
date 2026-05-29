package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.cache.CacheManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PracticeCatalogAspectProviderTest extends BaseUnitTest {

    @Mock
    PracticeRepository practiceRepository;

    @Mock
    WorkspaceRepository workspaceRepository;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    CacheManager cacheManager;

    @InjectMocks
    PracticeCatalogAspectProvider provider;

    @Test
    void writesCatalog() throws Exception {
        Workspace ws = new Workspace();
        ws.setWorkspaceSlug("acme");
        when(workspaceRepository.findById(eq(1L))).thenReturn(Optional.of(ws));

        Practice practice = new Practice();
        practice.setSlug("error-state-handling");
        practice.setName("Error State Handling");
        practice.setCriteria("Show an error view for failed network calls.");
        when(practiceRepository.findByWorkspaceIdAndActiveTrue(eq(1L))).thenReturn(List.of(practice));

        Map<String, byte[]> files = new HashMap<>();
        provider.contribute(new ContextRequest.MentorChatRequest(1L, 2L, UUID.randomUUID()), files);

        byte[] bytes = files.get("context/target/practice_catalog.json");
        assertThat(bytes).isNotNull();
        JsonNode root = objectMapper.readTree(bytes);
        assertThat(root.get("workspace").get("slug").asString()).isEqualTo("acme");
        assertThat(root.get("practices").isArray()).isTrue();
        assertThat(root.get("practices")).hasSize(1);
        JsonNode entry = root.get("practices").get(0);
        assertThat(entry.get("slug").asString()).isEqualTo("error-state-handling");
        assertThat(entry.get("displayName").asString()).isEqualTo("Error State Handling");
        assertThat(entry.get("criteria").asString()).contains("Show an error view");
        assertThat(entry.has("description")).isFalse();
    }
}
