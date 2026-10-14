package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.evidence.capture.ContentSource;
import de.tum.cit.aet.hephaestus.evidence.capture.MentorContextRequest;
import de.tum.cit.aet.hephaestus.mentor.spi.MentorPracticeQuery;
import de.tum.cit.aet.hephaestus.practices.PracticeGuidanceRules;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@RequiredArgsConstructor
public class PracticeCatalogContentSource implements ContentSource {
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "practice_catalog.json";
    private final MentorPracticeQuery practices;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MentorContextRequest request) {
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(MentorContextRequest request, Map<String, byte[]> files) {
        files.put(OUTPUT_KEY, objectMapper.writeValueAsBytes(buildPayload(request.workspaceId())));
    }

    public ObjectNode buildPayload(Long workspaceId) {
        var root = objectMapper.createObjectNode();
        var rows = root.putArray("practices");
        for (var entry : practices.current(workspaceId)) {
            var definition = entry.definition();
            var guidance = definition.guidance();
            rows.addObject()
                    .put("slug", entry.slug())
                    .put("displayName", definition.name())
                    .put("criteria", definition.criteria())
                    .put("whyItMatters", guidance.whyItMatters())
                    .put("whatGoodLooksLike", guidance.whatGoodLooksLike())
                    .put(
                            "visualDescription",
                            guidance.visual() == null ? null : guidance.visual().alt())
                    .put(
                            "guide",
                            guidance.guide() == null
                                    ? null
                                    : PracticeGuidanceRules.describeFigures(
                                            guidance.guide().markdown()));
        }
        return root;
    }
}
