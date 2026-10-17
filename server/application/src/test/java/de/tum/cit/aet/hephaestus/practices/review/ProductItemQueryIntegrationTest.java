package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.chat.item.ThreadEvents;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemIndexService;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRegistration;
import de.tum.cit.aet.hephaestus.integration.docs.item.DocumentEvents;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmItemEvents;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.SqlReadMeasurement;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import({ReviewIntegrationData.class, ReviewLookupData.class})
class ProductItemQueryIntegrationTest extends BaseIntegrationTest {
    @Autowired
    ReviewIntegrationData data;

    @Autowired
    ReviewLookupData lookup;

    @Autowired
    ProductItemQuery items;

    @Autowired
    ProductReferenceQuery references;

    @Autowired
    ItemIndexService index;

    @Autowired
    SqlReadMeasurement reads;

    @Autowired
    IssueRepository issues;

    @Autowired
    RepositoryToMonitorRepository monitors;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    void shouldReadOnlyTheTargetWhenUnrelatedScmSlackAndOutlineItemsExist() {
        var scenario = data.scenario();
        var target = lookup.work(scenario.workspace(), IdentityProviderType.GITHUB, true);
        String url = Objects.requireNonNull(Objects.requireNonNull(references
                        .items(target.workspaceId(), List.of(target.itemId()))
                        .get(target.itemId()))
                .url());
        var before = reads.measure(() -> items.atUrl(target.workspaceId(), url));
        assertThat(before.value()).contains(target);
        for (var kind : List.of(ScmItemEvents.PULL_REQUEST, ThreadEvents.THREAD, DocumentEvents.DOCUMENT)) {
            for (int n = 0; n < 12; n++) {
                index.upsert(new ItemRegistration(
                        target.workspaceId(),
                        kind,
                        ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE),
                        null,
                        null));
            }
        }
        var after = reads.measure(() -> items.atUrl(target.workspaceId(), url));
        assertThat(after.value()).contains(target);
        assertThat(after.cost()).isEqualTo(before.cost());
        assertThat(after.sql()).noneMatch(sql -> sql.contains("slack_thread") || sql.contains("outline_document"));
        assertThat(items.atUrl(target.workspaceId(), "https://github.com/unknown/project/pull/5"))
                .isEmpty();
        assertThat(items.atUrl(data.scenario().workspace().getId(), url)).isEmpty();
    }

    @Test
    void shouldRejectTheAddressWhenWorkIsRemovedDeletedOrNoLongerMonitored() {
        var scenario = data.scenario();
        var target = lookup.work(scenario.workspace(), IdentityProviderType.GITLAB, false);
        var reference = Objects.requireNonNull(
                references.items(target.workspaceId(), List.of(target.itemId())).get(target.itemId()));
        String url = Objects.requireNonNull(reference.url());
        assertThat(items.atUrl(target.workspaceId(), url)).contains(target);
        index.remove(target.workspaceId(), target.itemId(), Instant.now());
        assertThat(items.atUrl(target.workspaceId(), url)).isEmpty();
        index.restore(target.workspaceId(), target.itemId());
        var work = issues.findById(Objects.requireNonNull(target.sourceId())).orElseThrow();
        work.setDeletedAt(Instant.now());
        issues.saveAndFlush(work);
        assertThat(items.atUrl(target.workspaceId(), url)).isEmpty();
        work.setDeletedAt(null);
        issues.saveAndFlush(work);
        var monitor = monitors.findByWorkspaceIdAndNameWithOwner(
                        target.workspaceId(), Objects.requireNonNull(reference.container()))
                .orElseThrow();
        monitors.delete(monitor);
        assertThat(items.atUrl(target.workspaceId(), url)).isEmpty();
    }
}
