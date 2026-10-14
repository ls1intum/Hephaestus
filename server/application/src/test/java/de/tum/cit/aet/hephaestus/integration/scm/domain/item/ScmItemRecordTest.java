package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReviewRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.ItemFixtures;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ScmItemRecordTest extends BaseUnitTest {
    private static final String BASE = "a".repeat(40), HEAD = "b".repeat(40), BLOB = "c".repeat(40);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScmItemAccess items = mock(ScmItemAccess.class);
    private final RepositoryRepository repositories = mock(RepositoryRepository.class);
    private final ScmWorkLookup workLookup = mock(ScmWorkLookup.class);
    private final PullRequestReviewRepository reviews = mock(PullRequestReviewRepository.class);
    private final ScmItemService service = new ScmItemService(
            items,
            mock(ScmRepositoryAccess.class),
            repositories,
            workLookup,
            reviews,
            mock(IssueEvidenceVersion.class),
            mock(ScmGitTree.class),
            mock(ScmFileVersions.class),
            mapper,
            mock(ScmWorkCapture.class),
            mock(ScmCaptureContributors.class),
            Clock.systemUTC());

    @ParameterizedTest
    @ValueSource(strings = {"scm.issue", "scm.pull_request", "scm.pull_request_review", "scm.repository", "scm.file"})
    void shouldReadTheGovernedRecordBytesWithoutCallingMirrors(String value) {
        var pinned = snapshot(value);
        var record = mapper.createObjectNode();
        if (value.equals("scm.file") || value.equals("scm.repository")) {
            record.put("repositoryId", "3");
            record.put("providerId", "5");
        } else record.put("body", "Captured words\nQuoted β");
        if (pinned.item().kind().equals(ScmItemEvents.FILE)) {
            record.put("path", pinned.item().path());
            record.put("blobId", BLOB);
            record.put("commitId", HEAD);
        } else if (pinned.item().kind().equals(ScmItemEvents.REPOSITORY)) {
            record.put("repositoryId", "3");
            record.put("commitId", HEAD);
        } else record.put("commit_sha", HEAD);
        String path = value.equals("scm.file") || value.equals("scm.repository")
                ? "context/item.json"
                : "context/metadata.json";
        var source = service;
        assertThat(source.record(pinned, Map.of(path, mapper.writeValueAsBytes(record))))
                .isEqualTo(record);
        verifyNoInteractions(items, repositories, workLookup, reviews);
    }

    @Test
    void shouldRefuseMissingOrMalformedGovernedBytesWithoutFallingBackToCurrentState() {
        var pinned = snapshot("scm.issue");
        var source = service;
        for (var files : List.<Map<String, byte[]>>of(
                Map.of(),
                Map.of("context/metadata.json", new byte[0]),
                Map.of("context/metadata.json", "null".getBytes(StandardCharsets.UTF_8)),
                Map.of("context/metadata.json", "{} {}".getBytes(StandardCharsets.UTF_8)),
                Map.of("context/metadata.json", "[1]".getBytes(StandardCharsets.UTF_8)),
                Map.of("context/metadata.json", "invalid JSON".getBytes(StandardCharsets.UTF_8)))) {
            assertThatThrownBy(() -> source.record(pinned, files))
                    .isInstanceOfSatisfying(
                            ItemCaptureException.class,
                            failure -> assertThat(failure.code()).isEqualTo(ItemCaptureCode.CAPTURE_FAILED));
        }
        verifyNoInteractions(items, repositories, workLookup, reviews);
    }

    @ParameterizedTest
    @ValueSource(strings = {"path", "blobId", "commitId"})
    void shouldRefuseAFileRecordThatContradictsThePinnedFile(String changed) {
        var pinned = snapshot("scm.file");
        var record = mapper.createObjectNode();
        record.put("path", "src/a.ts");
        record.put("blobId", BLOB);
        record.put("commitId", HEAD);
        record.put(changed, changed.equals("path") ? "src/other.ts" : "d".repeat(40));
        assertThatThrownBy(() -> service.record(pinned, Map.of("context/item.json", mapper.writeValueAsBytes(record))))
                .isInstanceOfSatisfying(
                        ItemCaptureException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ItemCaptureCode.VERSION_UNAVAILABLE));
    }

    @Test
    void shouldPropagateProgrammingFailuresWithoutCallingThemMalformedRecords() {
        var brokenMapper = mock(ObjectMapper.class);
        var failure = new NullPointerException("Invalid reader configuration");
        when(brokenMapper.reader()).thenThrow(failure);
        ReflectionTestUtils.setField(service, "mapper", brokenMapper);

        assertThatThrownBy(() -> service.record(snapshot("scm.issue"), Map.of("context/metadata.json", new byte[0])))
                .isSameAs(failure);
    }

    private ItemSnapshot snapshot(String value) {
        ItemKind kind = new ItemKind(value);
        ItemRef item = kind.equals(ScmItemEvents.FILE)
                ? new ItemRef(1, 2, kind, null, 4L, "src/a.ts")
                : new ItemRef(1, 2, kind, 3L, kind.equals(ScmItemEvents.REPOSITORY) ? null : 4L, null);
        ItemVersion version =
                switch (value) {
                    case "scm.file" -> new ItemVersion("scm.file:v1:" + BLOB + ":" + "e".repeat(64), HEAD);
                    case "scm.repository" -> new ItemVersion(HEAD, null);
                    case "scm.pull_request" -> ScmVersion.pullRequest(BASE, HEAD, "Title", null);
                    case "scm.pull_request_review" ->
                        ScmVersion.pullRequestReview(
                                BASE, HEAD, "Title", null, new ScmVersion.ReviewContent(HEAD, "Review", "COMMENTED"));
                    default -> new ItemVersion("opaque", null);
                };
        return ItemFixtures.snapshot(item, Map.of())
                .version(version)
                .integration(new IntegrationRef(IntegrationKind.GITHUB, 1, null))
                .label(ItemFixtures.label()
                        .itemId(2)
                        .title("Work")
                        .noun("item")
                        .integration(IntegrationKind.GITHUB)
                        .build())
                .build();
    }
}
