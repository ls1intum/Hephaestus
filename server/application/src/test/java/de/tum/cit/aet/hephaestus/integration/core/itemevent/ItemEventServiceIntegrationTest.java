package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.FoundBy;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventInput;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventReason;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRegistration;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemWriter;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ItemEventServiceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private ItemWriter writer;

    @Autowired
    private ItemEventService service;

    @Autowired
    private ItemEventRepository events;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private IdentityProviderRepository providers;

    @Autowired
    private UserRepository users;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private AccountIdentityQuery identities;

    @Test
    void shouldRetainABotProviderActorSeparatelyFromTheRequestingAccount() {
        var provider = providers.saveAndFlush(new IdentityProvider(
                IdentityProviderType.GITHUB, "https://events-" + UUID.randomUUID() + ".example.test"));
        var bot = TestUserFactory.createUser(731L, "event-bot", provider);
        bot.setType(User.Type.BOT);
        bot = users.saveAndFlush(bot);
        var requester = accounts.saveAndFlush(new Account("Requester"));
        var input = input();
        var id = writer.record(new ItemEventInput(
                input.workspaceId(),
                input.itemId(),
                input.name(),
                input.itemVersion(),
                input.occurredAt(),
                FoundBy.SYNC,
                bot.getId(),
                requester.getId()));
        assertThat(writer.record(new ItemEventInput(
                        input.workspaceId(),
                        input.itemId(),
                        input.name(),
                        input.itemVersion(),
                        input.occurredAt().plusSeconds(5),
                        FoundBy.WEBHOOK,
                        null,
                        null)))
                .isEqualTo(id);
        var recorded = events.findByIdAndWorkspaceId(id, input.workspaceId()).orElseThrow();
        assertThat(recorded.getActorUserId()).isEqualTo(bot.getId());
        assertThat(recorded.getRequestedById()).isEqualTo(requester.getId());
        assertThat(identities.resolveAccountId(Objects.requireNonNull(provider.getId()), "731", null))
                .isEmpty();
    }

    @Test
    void shouldPreserveFirstRecordedFactsOnRedelivery() {
        var input = input();
        UUID id = writer.record(input);
        assertThat(writer.record(new ItemEventInput(
                        input.workspaceId(),
                        input.itemId(),
                        input.name(),
                        input.itemVersion(),
                        input.occurredAt().plusSeconds(1),
                        FoundBy.SYNC,
                        null,
                        null)))
                .isEqualTo(id);
        var event = events.findByIdAndWorkspaceId(id, input.workspaceId()).orElseThrow();
        assertThat(event.getOccurredAt()).isEqualTo(input.occurredAt());
        assertThat(event.getFoundBy()).isEqualTo(FoundBy.WEBHOOK);
    }

    @Test
    void shouldKeepVersionAndOccurrenceWhileWaiting() {
        var input = input();
        UUID id = writer.record(input);
        Instant retry = input.occurredAt().plusSeconds(60);
        assertThat(service.decide(
                        input.workspaceId(),
                        id,
                        ItemEventState.RECORDED,
                        ItemEventState.WAITING,
                        ItemEventReason.BUDGET_EXHAUSTED,
                        retry))
                .isTrue();
        assertThat(service.decide(input.workspaceId(), id, ItemEventState.RECORDED, ItemEventState.STARTED, null, null))
                .isFalse();
        var event = events.findByIdAndWorkspaceId(id, input.workspaceId()).orElseThrow();
        assertThat(event.getOccurredAt()).isEqualTo(input.occurredAt());
        assertThat(event.getItemVersion()).isEqualTo(input.itemVersion());
        assertThat(event.getRetryAt()).isEqualTo(retry);
    }

    @Test
    void shouldRejectSyntheticEventOriginsAndForeignWorkspace() {
        var input = input();
        assertThatThrownBy(() -> writer.record(new ItemEventInput(
                        input.workspaceId(),
                        input.itemId(),
                        input.name(),
                        input.itemVersion(),
                        input.occurredAt(),
                        FoundBy.REQUEST,
                        null,
                        null)))
                .isInstanceOf(IllegalArgumentException.class);
        long foreign = workspaces
                .save(WorkspaceTestFixtures.activeWorkspace("event-foreign-" + UUID.randomUUID()))
                .getId();
        assertThatThrownBy(() -> writer.record(new ItemEventInput(
                        foreign,
                        input.itemId(),
                        input.name(),
                        input.itemVersion(),
                        input.occurredAt(),
                        FoundBy.WEBHOOK,
                        null,
                        null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldDeduplicateConcurrentRecorders() throws Exception {
        var input = input();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return writer.record(input);
            });
            var second = executor.submit(() -> {
                start.await();
                return writer.record(input);
            });
            start.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(second.get(30, TimeUnit.SECONDS));
        }
    }

    @Test
    void shouldRefuseForeignDecisionsAndKeepTerminalFactsOnReplay() {
        var input = input();
        UUID id = writer.record(input);
        long foreign = workspaces
                .save(WorkspaceTestFixtures.activeWorkspace("decision-foreign-" + UUID.randomUUID()))
                .getId();
        assertThat(service.decide(foreign, id, ItemEventState.RECORDED, ItemEventState.STARTED, null, null))
                .isFalse();
        assertThat(events.findByIdAndWorkspaceId(id, input.workspaceId())
                        .orElseThrow()
                        .getState())
                .isEqualTo(ItemEventState.RECORDED);
        assertThat(service.decide(
                        input.workspaceId(),
                        id,
                        ItemEventState.RECORDED,
                        ItemEventState.WITHHELD,
                        ItemEventReason.OUT_OF_REVIEW_SCOPE,
                        null))
                .isTrue();
        assertThat(writer.record(input)).isEqualTo(id);
        var terminal = events.findByIdAndWorkspaceId(id, input.workspaceId()).orElseThrow();
        assertThat(terminal.getState()).isEqualTo(ItemEventState.WITHHELD);
        assertThat(terminal.getReason()).isEqualTo(ItemEventReason.OUT_OF_REVIEW_SCOPE);
        assertThat(terminal.getOccurredAt()).isEqualTo(input.occurredAt());
        assertThatThrownBy(() -> service.decide(
                        input.workspaceId(), id, ItemEventState.WITHHELD, ItemEventState.STARTED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ItemEventInput input() {
        long workspace = workspaces
                .save(WorkspaceTestFixtures.activeWorkspace("event-" + UUID.randomUUID()))
                .getId();
        var item = writer.upsert(new ItemRegistration(workspace, new ItemKind("scm.issue"), 1L, null, null));
        return new ItemEventInput(
                workspace,
                item.itemId(),
                new ItemEventName("scm.issue.updated"),
                "test-version",
                Instant.parse("2026-10-14T10:00:00Z"),
                FoundBy.WEBHOOK,
                null,
                null);
    }
}
